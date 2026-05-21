import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.text.SimpleDateFormat;

/**
 * Auditor de Consistência Global.
 * Escuta todos os eventos de ocupação de drones para garantir que as propriedades
 * de exclusão mútua e não-duplicidade não sejam violadas no sistema distribuído.
 */
public class Validador {
    private final Map<Integer, String> droneOcupadoPor = new ConcurrentHashMap<>();
    private final Map<String, Integer> missaoAtendidaPor = new ConcurrentHashMap<>();

    private int totalAlertasEnviados = 0;
    private int violacoesDetectadas = 0;
    private long timestampUltimoErro = 0;
    private final int DURACAO_ALERTA_MS = 2000;

    private PrintWriter logWriter;

    private final String AMARELO = "\u001B[33m";
    private final String VERMELHO = "\u001B[31m";
    private final String VERDE = "\u001B[32m";
    private final String RESET = "\u001B[0m";

    public Validador(int porta) {
        inicializarLog();
        escreverLog("--- INICIANDO NOVA SESSÃO DE AUDITORIA ---");

        // Thread de interface: limpa a tela e renderiza o status periodicamente
        new Thread(() -> {
            while (true) {
                renderizar();
                try { Thread.sleep(500); } catch (Exception e) {}
            }
        }).start();

        iniciarServidor(porta);
    }

    private void inicializarLog() {
        try {
            FileWriter fw = new FileWriter("log_consistencia.txt", false);
            this.logWriter = new PrintWriter(new BufferedWriter(fw));
        } catch (IOException e) { e.printStackTrace(); }
    }

    private synchronized void escreverLog(String mensagem) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
        if (logWriter != null) { logWriter.println("[" + ts + "] " + mensagem); logWriter.flush(); }
    }

    private void iniciarServidor(int porta) {
        try (ServerSocket server = new ServerSocket(porta)) {
            while (true) {
                Socket s = server.accept();
                new Thread(() -> {
                    try (ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                        Mensagem msg = (Mensagem) in.readObject();
                        validar(msg);
                    } catch (Exception e) { }
                }).start();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Valida as regras de negócio do sistema:
     * 1. Um drone não pode estar em duas missões diferentes simultaneamente.
     * 2. Uma missão não pode ser atendida por dois drones diferentes.
     */
    private synchronized void validar(Mensagem msg) {
        int tipo = msg.getTipo();
        int idDrone = msg.getIdDrone();
        String idMissao = msg.getConteudo();

        if (tipo == Mensagem.DRONE_BUSY) {
            totalAlertasEnviados++;

            // Verificação de Exclusão Mútua de Recurso (Drone)
            if (droneOcupadoPor.containsKey(idDrone) && !droneOcupadoPor.get(idDrone).equals(idMissao)) {
                registrarViolacao("CONFLITO: Drone " + idDrone + " em [" + droneOcupadoPor.get(idDrone) + "] e [" + idMissao + "]");
            }
            // Verificação de Não-Duplicidade de Atendimento (Missão)
            if (missaoAtendidaPor.containsKey(idMissao) && missaoAtendidaPor.get(idMissao) != idDrone) {
                registrarViolacao("DUPLICIDADE: Missão " + idMissao + " em Drones " + missaoAtendidaPor.get(idMissao) + " e " + idDrone);
            }

            droneOcupadoPor.put(idDrone, idMissao);
            missaoAtendidaPor.put(idMissao, idDrone);
        } else if (tipo == Mensagem.DRONE_RELEASED || tipo == Mensagem.DRONE_FAILED) {
            if (idDrone > 0) {
                String mId = droneOcupadoPor.remove(idDrone);
                if (mId != null) missaoAtendidaPor.remove(mId);
            }
        }
        renderizar();
    }

    private void registrarViolacao(String erro) {
        violacoesDetectadas++;
        timestampUltimoErro = System.currentTimeMillis();
        escreverLog("!!! VIOLAÇÃO: " + erro);
    }

    private synchronized void renderizar() {
        String hora = new SimpleDateFormat("HH:mm:ss").format(new Date());
        long agora = System.currentTimeMillis();

        boolean alertaAtivo = (agora - timestampUltimoErro) < DURACAO_ALERTA_MS;

        System.out.print("\033[H\033[2J\033[3J");
        System.out.flush();

        System.out.println(AMARELO + "╔═════════════════════════════════════════════════════════════╗");
        System.out.println("║               AUDITORIA DE CONSISTÊNCIA GLOBAL              ║");
        System.out.println("╠═════════════════════════════════════════════════════════════╣");

        String info = String.format("  Mensagens Processadas: %-14d          Erros: %-5d", totalAlertasEnviados, violacoesDetectadas);
        System.out.println("║" + RESET + String.format("%-61s", info) + AMARELO + "║");

        String corStatus = alertaAtivo ? VERMELHO : VERDE;
        String textoStatus = alertaAtivo ? "INCONSISTÊNCIA DETECTADA" : "SISTEMA CONSISTENTE";

        System.out.print("║" + RESET + "  Status: " + corStatus + textoStatus + RESET);
        int espacosRestantes = 61 - (10 + textoStatus.length());
        for(int i=0; i<espacosRestantes; i++) System.out.print(" ");
        System.out.println(AMARELO + "║");

        System.out.println("╠══════════════════════╤══════════════════════════════════════╣");
        System.out.println("║       DRONE ID       │             MISSÃO ATUAL             ║");
        System.out.println("╟──────────────────────┼──────────────────────────────────────╢");

        if (droneOcupadoPor.isEmpty()) {
            System.out.println("║          -           │        Nenhum drone em missão        ║");
        } else {
            List<Integer> ids = new ArrayList<>(droneOcupadoPor.keySet());
            Collections.sort(ids);
            for (Integer id : ids) {
                System.out.println(String.format("║" + RESET + "       Drone %-9d" + AMARELO + "│" + RESET + "  %-36s" + AMARELO + "║", id, droneOcupadoPor.get(id)));
            }
        }
        System.out.println("╠══════════════════════╧══════════════════════════════════════╣");
        System.out.println("║" + RESET + String.format("%-61s", "  Log: ./log_consistencia.txt") + AMARELO + "║");
        System.out.println("║" + RESET + String.format("%-61s", "  Última verificação: " + hora) + AMARELO + "║");
        System.out.println("╚═════════════════════════════════════════════════════════════╝" + RESET);
    }

    public static void main(String[] args) { new Validador(6002); }
}