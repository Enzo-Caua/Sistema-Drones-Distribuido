import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Ferramenta de Teste de Carga e Concorrência.
 * Simula uma rajada de ocorrências simultâneas em múltiplos sensores para testar
 * a robustez do algoritmo de consenso e a integridade da fila global.
 */
public class TesteEstresse {
    public static void main(String[] args) throws InterruptedException {
        int numAlertas = 100; // Quantidade de missões a serem geradas instantaneamente
        String brokerHost = "127.0.0.1";
        int[] portasBrokers = {5001, 5002, 5003, 5004};

        for (int i = 0; i < numAlertas; i++) {
            int porta = portasBrokers[i % 4]; // Distribui a carga entre os 4 brokers disponíveis
            int finalI = i;

            // Cada alerta é enviado em uma thread própria para garantir simultaneidade real
            new Thread(() -> {
                try (Socket s = new Socket(brokerHost, porta);
                     ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {

                    // Envia alertas com urgência aleatória (1 a 5)
                    out.writeObject(new Mensagem(Mensagem.SENSOR_ALERTA, 0, 999, "STRESS_"+finalI, (int)(Math.random()*5)+1, 0));
                    out.flush();
                } catch (Exception e) {
                    System.err.println("Erro no teste de carga: " + e.getMessage());
                }
            }).start();

            // Pequeno intervalo para não sobrecarregar o buffer de sockets do SO local
            if (i % 10 == 0) Thread.sleep(50);
        }
    }
}