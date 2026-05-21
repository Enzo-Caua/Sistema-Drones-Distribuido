import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class Broker {
    private final int id, porta;
    private final List<String> vizinhos;
    private final List<String> enderecosDrones;
    private int relogioLamport = 0;

    public enum Estado { RELEASED, WANTED, HELD }
    private volatile Estado estadoAtual = Estado.RELEASED;

    private final PriorityQueue<Missao> filaGlobal = new PriorityQueue<>();
    private final Set<String> acksConfirmados = Collections.synchronizedSet(new HashSet<>());
    private final List<Mensagem> filaDeEsperaConsenso = new ArrayList<>();

    private int quantidadeVotos = 0;
    private int votosEsperados = 0;
    private int timestampPedido = 0;
    private int prioridadePedido = 0;

    private final Map<Integer, Boolean> frotaDrones = new ConcurrentHashMap<>();

    public Broker(int id, int porta, List<String> vizinhos, List<String> drones) {
        this.id = id; this.porta = porta;
        this.vizinhos = vizinhos;
        this.enderecosDrones = drones;
        for (int i = 1; i <= 8; i++) frotaDrones.put(i, true);
    }

    public void iniciar() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(porta)) {
                System.out.println("\u001B[32m[SISTEMA] Broker " + id + " online.\u001B[0m");
                while (true) new Thread(new GerenciadorMsg(server.accept(), this)).start();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();

        enviarParaTodos("SYNC", 0, 0, Mensagem.SYNC_STATE);

        new Thread(() -> {
            while (true) {
                String dashboardHost = System.getenv().getOrDefault("DASHBOARD_HOST", "dashboard");
                try (Socket s = new Socket(dashboardHost, 7000);
                     ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                    out.writeObject(new Mensagem(Mensagem.HEARTBEAT, 0, this.id, "Broker " + this.id, this.porta, 0));
                    out.flush();
                    Thread.sleep(2000);
                } catch (Exception e) { }
            }
        }).start();

        new Thread(() -> {
            try { Thread.sleep(3000); enviarParaTodos("GET_QUEUE", 0, 0, Mensagem.SYNC_QUEUE_REQUEST); } catch (Exception e) {}
        }).start();

        new Thread(this::processarFilaMissoes).start();
    }

    private void processarFilaMissoes() {
        while (true) {
            try {
                Missao proxima = null;
                synchronized (this) { proxima = filaGlobal.peek(); }
                if (proxima != null && estadoAtual == Estado.RELEASED && buscarDroneLivre() != -1) {
                    if (!proxima.idUnico.startsWith("B" + this.id)) Thread.sleep(new Random().nextInt(300));
                    realizarCicloDeMissao(proxima);
                }
                Thread.sleep(100);
            } catch (Exception e) {}
        }
    }

    public void realizarCicloDeMissao(Missao missao) {
        try {
            synchronized (this) {
                this.estadoAtual = Estado.WANTED;
                this.timestampPedido = getTempoEIncrementar();
                this.prioridadePedido = missao.getPrioridadeEfetiva();
                this.quantidadeVotos = 0;
            }
            enviarParaTodos("ACESSO", prioridadePedido, 0, Mensagem.REQUEST);
            int tentativas = 0;
            while (this.estadoAtual != Estado.HELD && tentativas < 100) { Thread.sleep(50); tentativas++; }
            if (this.estadoAtual != Estado.HELD) { liberarConsenso(); return; }

            synchronized (this) {
                int droneID = buscarDroneLivre();
                if (droneID != -1 && filaGlobal.contains(missao)) {
                    removerMissaoFila(missao.idUnico);
                    enviarParaTodos(missao.idUnico, 0, 0, Mensagem.QUEUE_REMOVE);
                    if (comandarDrone(droneID, missao)) {
                        atualizarStatusDrone(droneID, false, this.id, missao.prioridadeBase, missao.idUnico);
                    } else { registrarAlertaSensor(missao.prioridadeBase); }
                }
            }
            liberarConsenso();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void liberarConsenso() {
        this.estadoAtual = Estado.RELEASED;
        for (Mensagem m : new ArrayList<>(filaDeEsperaConsenso)) enviarMensagemUnica(m.getIdRemetente(), Mensagem.REPLY, "OK", 0);
        filaDeEsperaConsenso.clear();
    }

    private boolean comandarDrone(int droneID, Missao m) {
        try {
            String endereco = enderecosDrones.get(droneID - 1);
            String[] partes = endereco.split(":");
            Socket s = new Socket();
            s.connect(new InetSocketAddress(partes[0], Integer.parseInt(partes[1])), 1000);
            ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
            out.writeObject(new Mensagem(Mensagem.DRONE_CMD, getTempoEIncrementar(), this.id, m.idUnico, m.prioridadeBase, droneID));
            out.flush(); s.close();
            return true;
        } catch (Exception e) { return false; }
    }

    public void registrarAlertaSensor(int urgencia) {
        String idM = "B" + id + "_" + System.nanoTime();
        int tempo = getTempoEIncrementar();
        adicionarMissaoFila(idM, urgencia, tempo);
        enviarParaTodos(idM, urgencia, 0, Mensagem.QUEUE_ADD);
    }

    public synchronized void adicionarMissaoFila(String idM, int prio, int ts) {
        Missao m = new Missao(idM, prio, ts);
        if (!filaGlobal.contains(m)) filaGlobal.add(m);
        avisarMonitorFila(Mensagem.QUEUE_ADD, idM, prio, ts);
    }

    public synchronized void removerMissaoFila(String idM) {
        filaGlobal.removeIf(m -> m.idUnico.equals(idM));
        avisarMonitorFila(Mensagem.QUEUE_REMOVE, idM, 0, 0);
    }

    private void avisarMonitorFila(int t, String id, int p, int ts) {
        String host = System.getenv().getOrDefault("MONITOR_FILA_HOST", "monitor-fila");
        try (Socket s = new Socket(host, 6001);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
            out.writeObject(new Mensagem(t, ts, this.id, id, p, 0));
        } catch (Exception e) { }
    }

    public void enviarParaTodos(String txt, int urg, int idD, int tipo) {
        int tempo = getTempoEIncrementar();
        List<String> copiaVizinhos = new ArrayList<>(vizinhos);
        if (tipo == Mensagem.REQUEST) this.votosEsperados = copiaVizinhos.size();
        for (String v : copiaVizinhos) {
            new Thread(() -> {
                Mensagem m = new Mensagem(tipo, tempo, this.id, txt, urg, idD);
                if (!enviarComRetry(v, m, 3) && tipo == Mensagem.REQUEST) {
                    synchronized (this) { this.votosEsperados--; verificarVotos(); }
                }
            }).start();
        }
    }

    public boolean enviarComRetry(String dest, Mensagem m, int tent) {
        for (int i = 0; i < tent; i++) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(dest.split(":")[0], Integer.parseInt(dest.split(":")[1])), 500);
                ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                out.writeObject(m); out.flush();
                if (m.getTipo() == Mensagem.QUEUE_ADD || m.getTipo() == Mensagem.QUEUE_REMOVE || m.getTipo() == Mensagem.REQUEST) {
                    return aguardarACK(m.getMsgId());
                }
                return true;
            } catch (Exception e) { try { Thread.sleep(200); } catch(Exception ex){} }
        }
        return false;
    }

    public synchronized void atualizarStatusDrone(int idD, boolean l, int idB, int u, String idM) {
        frotaDrones.put(idD, l);
        Mensagem mStatus = new Mensagem(l ? Mensagem.DRONE_RELEASED : Mensagem.DRONE_BUSY, getTempoEIncrementar(), idB, idM, u, idD);

        String monHost = System.getenv().getOrDefault("MONITOR_GLOBAL_HOST", "monitor-global");
        String valHost = System.getenv().getOrDefault("VALIDATOR_HOST", "validator");

        enviarMensagemRede(monHost + ":6000", mStatus);
        enviarMensagemRede(valHost + ":6002", mStatus);
    }

    private void enviarMensagemRede(String dest, Mensagem m) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(dest.split(":")[0], Integer.parseInt(dest.split(":")[1])), 500);
            ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
            out.writeObject(m); out.flush();
        } catch (Exception e) { }
    }

    public void enviarMensagemUnica(int idDest, int tipo, String txt, int idDrone) {
        String portaAlvo = ":" + (5000 + idDest);
        vizinhos.stream().filter(v -> v.contains(portaAlvo)).findFirst()
                .ifPresent(v -> enviarMensagemRede(v, new Mensagem(tipo, getTempoEIncrementar(), this.id, txt, 0, idDrone)));
    }

    public void enviarMensagemEstado(int idDest, List<Missao> fila) {
        String portaAlvo = ":" + (5000 + idDest);
        vizinhos.stream().filter(v -> v.contains(portaAlvo)).findFirst().ifPresent(v -> {
            Mensagem m = new Mensagem(Mensagem.SYNC_QUEUE_RESPONSE, getTempoEIncrementar(), this.id, "STATE", 0, 0);
            m.setObjetoExtra(fila);
            enviarMensagemRede(v, m);
        });
    }

    public synchronized void registrarACK(String id) { acksConfirmados.add(id); }
    private boolean aguardarACK(String id) {
        long lim = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < lim) { if (acksConfirmados.remove(id)) return true; try { Thread.sleep(50); } catch(Exception e){} }
        return false;
    }
    public synchronized void incrementarVotos() { this.quantidadeVotos++; verificarVotos(); }
    private synchronized void verificarVotos() { if (this.quantidadeVotos >= votosEsperados && estadoAtual == Estado.WANTED) this.estadoAtual = Estado.HELD; }
    public synchronized int buscarDroneLivre() { return frotaDrones.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).findFirst().orElse(-1); }
    public synchronized int getTempoEIncrementar() { return ++relogioLamport; }
    public synchronized void atualizarRelogio(int t) { this.relogioLamport = Math.max(this.relogioLamport, t) + 1; }
    public void adicionarNaFilaConsenso(Mensagem m) { synchronized(filaDeEsperaConsenso) { filaDeEsperaConsenso.add(m); } }
    public synchronized List<Missao> getFilaGlobal() { return new ArrayList<>(filaGlobal); }
    public Estado getEstadoAtual() { return estadoAtual; }
    public int getPrioridadePedido() { return prioridadePedido; }
    public int getTimestampPedido() { return timestampPedido; }
    public int getId() { return id; }
    public void tratarFalhaDrone(int d, int u) { registrarAlertaSensor(u); atualizarStatusDrone(d, true, 0, 0, "-"); }
    public Map<Integer, Boolean> getFrotaDrones() { return frotaDrones; }

    public static void main(String[] args) {
        new Broker(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Arrays.asList(args[2].split(",")), Arrays.asList(args[3].split(","))).iniciar();
    }
}