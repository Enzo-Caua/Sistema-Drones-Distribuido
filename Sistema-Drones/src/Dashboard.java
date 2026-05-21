import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Painel de Saúde do Sistema.
 * Recebe heartbeats de todos os componentes (Brokers, Drones, Sensores) para
 * monitorar a disponibilidade da rede em tempo real.
 */
public class Dashboard {
    private static class ComponenteInfo {
        String tipo;
        long lastSeen;
        String ip;
        int portaServico;
    }

    private final Map<String, ComponenteInfo> componentes = new ConcurrentHashMap<>();
    private final String AMARELO = "\u001B[33m";
    private final String VERDE = "\u001B[32m";
    private final String VERMELHO = "\u001B[31m";
    private final String RESET = "\u001B[0m";

    public Dashboard(int porta) {
        new Thread(this::ouvirHeartbeats).start();
        new Thread(this::renderizarLoop).start();
    }

    private void ouvirHeartbeats() {
        try (ServerSocket server = new ServerSocket(7000)) {
            while (true) {
                Socket s = server.accept();
                new Thread(() -> {
                    try (ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                        Mensagem msg = (Mensagem) in.readObject();
                        if (msg.getTipo() == Mensagem.HEARTBEAT) {
                            String chave = msg.getConteudo();
                            ComponenteInfo info = componentes.getOrDefault(chave, new ComponenteInfo());
                            info.tipo = chave;
                            info.lastSeen = System.currentTimeMillis();
                            info.ip = s.getInetAddress().getHostAddress();
                            info.portaServico = msg.getPrioridade();
                            componentes.put(chave, info);
                        }
                    } catch (Exception e) { }
                }).start();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void renderizarLoop() {
        while (true) {
            renderizar();
            try { Thread.sleep(2000); } catch (InterruptedException e) { }
        }
    }

    private synchronized void renderizar() {
        System.out.print("\033[H\033[2J\033[3J");
        System.out.flush();

        System.out.println(AMARELO + "╔═════════════════════════════════════════════════════════════╗");
        System.out.println("║                PAINEL DE SAÚDE DO SISTEMA                   ║");
        System.out.println("╠══════════════════════╤══════════════════╤═══════════════════╣");
        System.out.println("║      COMPONENTE      │      STATUS      │     ENDEREÇO      ║");
        System.out.println("╟──────────────────────┼──────────────────┼───────────────────╢");

        long agora = System.currentTimeMillis();
        List<String> chaves = new ArrayList<>(componentes.keySet());
        Collections.sort(chaves);

        for (String chave : chaves) {
            ComponenteInfo info = componentes.get(chave);
            // Considera o componente inativo se não receber pulso por mais de 6 segundos
            boolean ativo = (agora - info.lastSeen) < 6000;

            String statusTexto = ativo ? "ATIVO" : "INATIVO";
            String corStatus = ativo ? VERDE : VERMELHO;
            String endereco = info.ip + ":" + info.portaServico;

            System.out.print(AMARELO + "║" + RESET + String.format("  %-18s  ", chave) +
                    AMARELO + "│" + RESET + corStatus + String.format("  %-14s  ", statusTexto) +
                    AMARELO + "│" + RESET + String.format("  %-17s", ativo ? endereco : "OFFLINE") +
                    AMARELO + "║\n");
        }

        System.out.println(AMARELO + "╠══════════════════════╧══════════════════╧═══════════════════╣");
        String hora = new java.text.SimpleDateFormat("HH:mm:ss").format(new Date());
        System.out.println("║  Última verificação: " + hora + "                               ║");
        System.out.println("╚═════════════════════════════════════════════════════════════╝" + RESET);
    }

    public static void main(String[] args) { new Dashboard(7000); }
}