import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Random;

/**
 * Entidade Sensor.
 * Detecta ocorrências e as reporta ao Broker do seu setor.
 * Implementa persistência de envio em caso de indisponibilidade temporária do Broker.
 */
public class Sensor {
    public static void main(String[] args) throws Exception {
        int brokerPort = Integer.parseInt(args[0]);
        int idSensor = Integer.parseInt(args[1]);
        String brokerHost = (args.length > 2) ? args[2] : "127.0.0.1";

        // Thread para notificação de pulso de vida ao Dashboard
        new Thread(() -> {
            while (true) {
                String dashboardHost = System.getenv().getOrDefault("DASHBOARD_HOST", "127.0.0.1");
                try (Socket s = new Socket(dashboardHost, 7000);
                     ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                    out.writeObject(new Mensagem(Mensagem.HEARTBEAT, 0, idSensor, "Sensor " + idSensor, brokerPort, 0));
                    out.flush();
                } catch (Exception e) { }
                try { Thread.sleep(2000); } catch (InterruptedException e) { }
            }
        }).start();

        Random r = new Random();
        System.out.println(String.format("[SENSOR %d] Iniciado. Conectando em %s:%d", idSensor, brokerHost, brokerPort));

        while (true) {
            // Simula intervalo entre ocorrências
            Thread.sleep(10000 + r.nextInt(1000));
            int urgencia = 1 + r.nextInt(5);
            boolean enviado = false;

            // Mecanismo de persistência: Tenta enviar o alerta até que o Broker confirme o recebimento
            while (!enviado) {
                try (Socket s = new Socket(brokerHost, brokerPort);
                     ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {

                    out.writeObject(new Mensagem(Mensagem.SENSOR_ALERTA, 0, idSensor, "ALERTA", urgencia, 0));
                    out.flush();
                    System.out.println("[SENSOR " + idSensor + "] Alerta enviado.");
                    enviado = true;
                } catch (Exception e) {
                    System.err.println("[ERRO] Broker offline. Tentando novamente em 5s...");
                    Thread.sleep(5000);
                }
            }
        }
    }
}