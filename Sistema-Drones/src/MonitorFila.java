import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interface de Monitoramento da Fila Global.
 * Exibe todas as requisições de sensores que aguardam atendimento por um drone.
 */
public class MonitorFila {
    /**
     * Representação visual de uma missão pendente na fila.
     */
    private static class MissaoInfo {
        String id;
        int prioridade;
        int timestamp;
        String origem;

        public MissaoInfo(String id, int prioridade, int timestamp) {
            this.id = id;
            this.prioridade = prioridade;
            this.timestamp = timestamp;
            // Extrai o ID do Broker de origem a partir do prefixo da missão (ex: B1_)
            this.origem = "Broker " + id.split("_")[0].substring(1);
        }
    }

    private final Map<String, MissaoInfo> filaEspera = new ConcurrentHashMap<>();
    private final String AMARELO = "\u001B[33m";
    private final String VERMELHO = "\u001B[31m";
    private final String VERDE = "\u001B[32m";
    private final String CIANO = "\u001B[36m";
    private final String RESET = "\u001B[0m";

    public MonitorFila(int porta) {
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
                        if (msg.getTipo() == Mensagem.QUEUE_ADD) {
                            filaEspera.put(msg.getConteudo(),
                                    new MissaoInfo(msg.getConteudo(), msg.getPrioridade(), msg.getTimestamp()));
                        } else if (msg.getTipo() == Mensagem.QUEUE_REMOVE) {
                            filaEspera.remove(msg.getConteudo());
                        }
                        renderizar();
                    } catch (Exception e) { }
                }).start();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Renderiza a fila ordenada por prioridade (urgência) e timestamp (ordem causal).
     */
    private synchronized void renderizar() {
        System.out.print("\033[H\033[2J\033[3J");
        System.out.flush();

        System.out.println(AMARELO + "╔═════════════════════════════════════════════════════════════╗");
        System.out.println("║                 FILA GLOBAL DE REQUISIÇÕES                  ║");
        System.out.println("╠══════════════════════╤══════════════════╤═══════════════════╣");
        System.out.println("║      ID MISSÃO       │    URGÊNCIA      │      ORIGEM       ║");
        System.out.println("╟──────────────────────┼──────────────────┼───────────────────╢");

        // Ordenação seguindo as regras de prioridade efetiva do sistema
        List<MissaoInfo> ordenada = new ArrayList<>(filaEspera.values());
        ordenada.sort((a, b) -> {
            if (a.prioridade != b.prioridade) return Integer.compare(b.prioridade, a.prioridade);
            return Integer.compare(a.timestamp, b.timestamp);
        });

        if (ordenada.isEmpty()) {
            System.out.println(AMARELO + "║                      │"+ RESET + "  [ Fila Vazia ]  " + AMARELO + "│                   ║");
        } else {
            for (MissaoInfo m : ordenada) {
                String corPrio = m.prioridade >= 4 ? VERMELHO : (m.prioridade >= 2 ? CIANO : VERDE);

                String col1 = String.format("  %-18s  ", m.id);
                String col2 = String.format("    Prio: %-8d", m.prioridade);
                String col3 = String.format("      %-11s  ", m.origem);

                System.out.print(AMARELO + "║" + RESET + col1 +
                        AMARELO + "│" + RESET + corPrio + col2 + RESET +
                        AMARELO + "│" + RESET + col3 +
                        AMARELO + "║\n");
            }
        }

        System.out.println(AMARELO + "╠══════════════════════╧══════════════════╧═══════════════════╣");

        String hora = new java.text.SimpleDateFormat("HH:mm:ss").format(new Date());
        String textoPendentes = "  Total de missões pendentes: " + ordenada.size();
        System.out.println(AMARELO + "║" + RESET + String.format("%-61s", textoPendentes) + AMARELO + "║");

        String textoHora = "  Última atualização: " + hora;
        System.out.println(AMARELO + "║" + RESET + String.format("%-61s", textoHora) + AMARELO + "║");

        System.out.println(AMARELO + "╚═════════════════════════════════════════════════════════════╝" + RESET);
    }

    public static void main(String[] args) { new MonitorFila(6001); }
}