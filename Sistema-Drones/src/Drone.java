import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Entidade Drone (Atuador).
 * Recebe comandos de missão dos Brokers e notifica a rede sobre seu status (ocupado, livre ou falha).
 */
public class Drone {
    private final int id;
    private final int porta;
    private final List<String> enderecosBrokers;
    private boolean ocupado = false;
    private final Random random = new Random();

    public Drone(int id, int porta, List<String> brokers) {
        this.id = id;
        this.porta = porta;
        this.enderecosBrokers = brokers;
    }

    public void iniciar() {
        // Envia pulso de vida periódico para o Dashboard de saúde
        new Thread(() -> {
            while (true) {
                String dashboardHost = System.getenv().getOrDefault("DASHBOARD_HOST", "127.0.0.1");
                try (Socket s = new Socket(dashboardHost, 7000);
                     ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                    out.writeObject(new Mensagem(Mensagem.HEARTBEAT, 0, this.id, "Drone " + this.id, this.porta, 0));
                    out.flush();
                } catch (Exception e) { }
                try { Thread.sleep(2000); } catch (InterruptedException e) { }
            }
        }).start();

        try (ServerSocket server = new ServerSocket(porta)) {
            System.out.println(String.format("\u001B[35m[DRONE %d] Inicializado na porta %d. Aguardando comandos...\u001B[0m", id, porta));

            while (true) {
                Socket socket = server.accept();
                new Thread(() -> processarComando(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("[ERRO] Falha crítica no servidor do Drone: " + e.getMessage());
        }
    }

    private void processarComando(Socket socket) {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            Mensagem msg = (Mensagem) in.readObject();

            if (msg.getTipo() == Mensagem.DRONE_CMD) {
                if (ocupado) return;
                executarMissao(msg.getPrioridade(), msg.getIdRemetente(), msg.getConteudo());
            }
        } catch (Exception e) { }
    }

    /**
     * Simula a execução da missão.
     * Possui uma pequena probabilidade de falha para testar a resiliência do sistema.
     */
    private void executarMissao(int urgencia, int idBroker, String idMissao) {
        this.ocupado = true;
        notificarBrokers(Mensagem.DRONE_BUSY, urgencia, idBroker, idMissao);

        try {
            // Simula tempo de deslocamento e execução
            Thread.sleep(2000 + random.nextInt(1000));

            // Probabilidade de 1% de falha catastrófica durante a missão
            if (random.nextDouble() < 0.01) {
                notificarBrokers(Mensagem.DRONE_FAILED, urgencia, idBroker, idMissao);
            } else {
                notificarBrokers(Mensagem.DRONE_RELEASED, 0, 0, "-");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            this.ocupado = false;
        }
    }

    /**
     * Realiza o broadcast do novo status do drone para todos os Brokers conhecidos.
     */
    private void notificarBrokers(int tipo, int urgencia, int idBrokerOrigem, String conteudo) {
        Mensagem status = new Mensagem(tipo, 0, idBrokerOrigem, conteudo, urgencia, this.id);

        for (String endereco : enderecosBrokers) {
            try {
                String[] partes = endereco.split(":");
                Socket s = new Socket();
                s.connect(new InetSocketAddress(partes[0], Integer.parseInt(partes[1])), 200);
                ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                out.writeObject(status);
                out.flush(); out.close(); s.close();
            } catch (Exception e) { }
        }
    }

    public static void main(String[] args) {
        if (args.length < 3) return;
        int id = Integer.parseInt(args[0]);
        int porta = Integer.parseInt(args[1]);
        List<String> brokers = Arrays.asList(args[2].split(","));
        new Drone(id, porta, brokers).iniciar();
    }
}