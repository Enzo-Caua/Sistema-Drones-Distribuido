import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interface de Visualização da Frota de Drones.
 * Consolida as mensagens de broadcast da rede para exibir em tempo real
 * qual drone está atendendo qual missão e em qual setor.
 */
public class Monitor {
    /**
     * Estrutura interna para armazenar o estado atual de cada drone na interface.
     */
    private static class DroneInfo {
        int id;
        String status = "LIVRE";
        String cor = "\u001B[32m";
        String setor = "-";
        int prioridade = 0;
        String missaoId = "-";
    }

    private final Map<Integer, DroneInfo> frota = new ConcurrentHashMap<>();
    private final String AMARELO = "\u001B[33m";
    private final String VERMELHO = "\u001B[31m";
    private final String VERDE = "\u001B[32m";
    private final String CIANO = "\u001B[36m";
    private final String RESET = "\u001B[0m";

    public Monitor(int porta) {
        // Inicializa a frota com os 8 drones padrão
        for (int i = 1; i <= 8; i++) {
            DroneInfo d = new DroneInfo();
            d.id = i;
            frota.put(i, d);
        }
        iniciarServidor(porta);
    }

    private void iniciarServidor(int porta) {
        try (ServerSocket server = new ServerSocket(porta)) {
            renderizar();
            while (true) {
                Socket s = server.accept();
                new Thread(() -> {
                    try (ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                        Mensagem msg = (Mensagem) in.readObject();
                        atualizarDados(msg);
                        renderizar();
                    } catch (Exception e) { }
                }).start();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Atualiza o estado local do drone baseado no tipo de mensagem recebida.
     */
    private void atualizarDados(Mensagem msg) {
        DroneInfo d = frota.get(msg.getIdDrone());
        if (d != null) {
            if (msg.getTipo() == Mensagem.DRONE_BUSY) {
                d.status = "OCUPADO";
                d.cor = VERMELHO;
                d.setor = "B" + msg.getIdRemetente();
                d.prioridade = msg.getPrioridade();
                d.missaoId = msg.getConteudo();
            } else if (msg.getTipo() == Mensagem.DRONE_RELEASED) {
                d.status = "LIVRE";
                d.cor = VERDE;
                d.setor = "-";
                d.prioridade = 0;
                d.missaoId = "-";
            }
        }
    }

    /**
     * Renderiza a tabela ASCII com o status global da frota.
     * Utiliza sequências de escape ANSI para formatação e cores.
     */
    private synchronized void renderizar() {
        System.out.print("\033[H\033[2J\033[3J");
        System.out.flush();

        System.out.println(AMARELO + "╔═════════════════════════════════════════════════════════════╗");
        System.out.println("║                MONITORAMENTO GLOBAL DA FROTA                ║");
        System.out.println("╠═══╤═════════╤══════════════════════╤══════════╤═════════════╣");
        System.out.println("║ ID│ STATUS  │      ID MISSÃO       │  SETOR   │ PRIORIDADE  ║");
        System.out.println("╟───┼─────────┼──────────────────────┼──────────┼─────────────╢");

        for (int i = 1; i <= 8; i++) {
            DroneInfo d = frota.get(i);

            // Define a cor da coluna de prioridade para facilitar a identificação visual de urgências
            String corPrio;
            if (d.status.equals("LIVRE")) {
                corPrio = RESET;
            } else {
                corPrio = d.prioridade >= 4 ? VERMELHO : (d.prioridade >= 2 ? CIANO : VERDE);
            }

            String colID   = String.format(" %-2d", d.id);
            String colST   = String.format(" %-8s", d.status);
            String colMiss = String.format(" %-20s ", d.missaoId);
            String colSet  = String.format(" %-8s ", d.setor);
            String colPrioStr = String.format(" Prio: %-6d", d.prioridade);

            System.out.print(AMARELO + "║" + RESET + colID +
                    AMARELO + "│" + d.cor + colST + RESET +
                    AMARELO + "│" + RESET + colMiss +
                    AMARELO + "│" + RESET + colSet +
                    AMARELO + "│" + corPrio + colPrioStr + RESET +
                    AMARELO + "║\n");
        }

        System.out.println(AMARELO + "╠═══╧═════════╧══════════════════════╧══════════╧═════════════╣");
        String hora = new java.text.SimpleDateFormat("HH:mm:ss").format(new Date());
        System.out.println("║  Última atualização: " + hora + "                               ║");
        System.out.println("╚═════════════════════════════════════════════════════════════╝" + RESET);
    }

    public static void main(String[] args) { new Monitor(6000); }
}