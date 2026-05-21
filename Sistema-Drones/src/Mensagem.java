import java.io.Serializable;

/**
 * Objeto de transferência de dados (DTO) para comunicação entre todos os nós.
 * Centraliza os tipos de eventos do protocolo e transporta o estado necessário
 * para sincronização e algoritmos de consenso.
 */
public class Mensagem implements Serializable {
    private static final long serialVersionUID = 1L;

    // Constantes que definem os tipos de mensagens trocadas no sistema
    public static final int REQUEST = 1;        // Solicitação de Seção Crítica (Ricart-Agrawala)
    public static final int REPLY = 2;          // Resposta/Voto de permissão (Ricart-Agrawala)
    public static final int DRONE_BUSY = 3;     // Notificação: Drone iniciou uma missão
    public static final int DRONE_RELEASED = 4; // Notificação: Drone concluiu e está livre
    public static final int SYNC_STATE = 5;     // Mensagem de controle para sincronia inicial
    public static final int SENSOR_ALERTA = 6;  // Alerta de ocorrência: Sensor -> Broker
    public static final int DRONE_CMD = 7;      // Comando de despacho: Broker -> Drone
    public static final int DRONE_STATUS = 8;   // Atualização periódica de estado do Drone
    public static final int DRONE_FAILED = 9;   // Notificação de erro catastrófico do Drone
    public static final int HEARTBEAT = 10;     // Pulso de vida para o Painel de Saúde
    public static final int QUEUE_ADD = 11;     // Inclusão de missão na fila global distribuída
    public static final int QUEUE_REMOVE = 12;  // Remoção de missão (quando assumida por um drone)
    public static final int ACK = 13;           // Confirmação de recebimento de mensagem
    public static final int SYNC_QUEUE_REQUEST = 14;  // Pedido de cópia da fila global
    public static final int SYNC_QUEUE_RESPONSE = 15; // Resposta contendo o estado da fila global

    private int tipo;
    private int timestamp;
    private int idRemetente;
    private String conteudo;
    private int prioridade;
    private int idDrone;
    private String msgId;

    /**
     * Campo genérico para transporte de objetos complexos (como a lista de missões)
     * durante procedimentos de sincronização de estado.
     */
    private Object objetoExtra;

    public Mensagem(int tipo, int timestamp, int idRemetente, String conteudo, int prioridade, int idDrone) {
        this.tipo = tipo;
        this.timestamp = timestamp;
        this.idRemetente = idRemetente;
        this.conteudo = conteudo;
        this.prioridade = prioridade;
        this.idDrone = idDrone;

        // Gera um identificador único para a mensagem para fins de rastreio e ACKs
        this.msgId = idRemetente + "_" + System.nanoTime();
    }

    public int getTipo() { return tipo; }
    public int getTimestamp() { return timestamp; }
    public int getIdRemetente() { return idRemetente; }
    public int getPrioridade() { return prioridade; }
    public int getIdDrone() { return idDrone; }
    public String getConteudo() { return conteudo; }
    public String getMsgId() { return msgId; }
    public Object getObjetoExtra() { return objetoExtra; }
    public void setObjetoExtra(Object objetoExtra) { this.objetoExtra = objetoExtra; }
}