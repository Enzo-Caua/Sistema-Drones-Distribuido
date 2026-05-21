import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Responsável pelo processamento de mensagens recebidas pelo Broker.
 * Implementa a lógica de decisão do protocolo Ricart-Agrawala para exclusão mútua distribuída.
 */
public class GerenciadorMsg implements Runnable {
    private Socket socket;
    private Broker broker;

    public GerenciadorMsg(Socket s, Broker b) { this.socket = s; this.broker = b; }

    @Override
    public void run() {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            Mensagem msg = (Mensagem) in.readObject();

            // Sincroniza o relógio lógico de Lamport local com o timestamp da mensagem recebida
            broker.atualizarRelogio(msg.getTimestamp());

            switch (msg.getTipo()) {
                case Mensagem.SENSOR_ALERTA:
                    broker.registrarAlertaSensor(msg.getPrioridade());
                    break;
                case Mensagem.DRONE_BUSY:
                    broker.atualizarStatusDrone(msg.getIdDrone(), false, msg.getIdRemetente(), msg.getPrioridade(), msg.getConteudo());
                    break;
                case Mensagem.DRONE_RELEASED:
                    broker.atualizarStatusDrone(msg.getIdDrone(), true, 0, 0, "-");
                    break;
                case Mensagem.DRONE_FAILED:
                    broker.tratarFalhaDrone(msg.getIdDrone(), msg.getPrioridade());
                    break;
                case Mensagem.REPLY:
                    broker.incrementarVotos();
                    break;
                case Mensagem.REQUEST:
                    enviarACK(msg);
                    decidirResposta(msg);
                    break;
                case Mensagem.QUEUE_ADD:
                    enviarACK(msg);
                    broker.adicionarMissaoFila(msg.getConteudo(), msg.getPrioridade(), msg.getTimestamp());
                    break;
                case Mensagem.QUEUE_REMOVE:
                    enviarACK(msg);
                    broker.removerMissaoFila(msg.getConteudo());
                    break;
                case Mensagem.ACK:
                    broker.registrarACK(msg.getConteudo());
                    break;
                case Mensagem.SYNC_QUEUE_REQUEST:
                    broker.enviarMensagemEstado(msg.getIdRemetente(), broker.getFilaGlobal());
                    break;
                case Mensagem.SYNC_QUEUE_RESPONSE:
                    List<Missao> filaRecebida = (List<Missao>) msg.getObjetoExtra();
                    if (filaRecebida != null) {
                        for(Missao m : filaRecebida) {
                            broker.adicionarMissaoFila(m.idUnico, m.prioridadeBase, m.timestampLamport);
                        }
                        System.out.println("\u001B[32m[SYNC] Fila sincronizada (" + filaRecebida.size() + " missões).\u001B[0m");
                    }
                    break;
            }
        } catch (Exception e) { }
    }

    /**
     * Lógica de decisão do algoritmo Ricart-Agrawala.
     * Determina se o Broker deve responder imediatamente com um REPLY ou
     * adiar a resposta caso ele próprio esteja tentando acessar a Seção Crítica.
     */
    private void decidirResposta(Mensagem msg) {
        boolean responderAgora = false;
        synchronized (broker) {
            if (broker.getEstadoAtual() == Broker.Estado.RELEASED) {
                // Broker não tem interesse na seção crítica: responde imediatamente
                responderAgora = true;
            } else if (broker.getEstadoAtual() == Broker.Estado.WANTED) {
                // Conflito de interesse: Comparação de prioridades (Ricart-Agrawala adaptado)
                if (msg.getPrioridade() > broker.getPrioridadePedido()) {
                    responderAgora = true; // Requisição externa é mais prioritária
                } else if (msg.getPrioridade() < broker.getPrioridadePedido()) {
                    responderAgora = false; // Minha requisição é mais prioritária
                } else {
                    // Empate de prioridade: desempate pelo Relógio de Lamport
                    if (msg.getTimestamp() < broker.getTimestampPedido()) responderAgora = true;
                    else if (msg.getTimestamp() > broker.getTimestampPedido()) responderAgora = false;
                    else responderAgora = (msg.getIdRemetente() < broker.getId()); // Desempate final pelo ID
                }

                if (!responderAgora) broker.adicionarNaFilaConsenso(msg);
            } else {
                // Estado HELD: O broker está na seção crítica, adia todas as respostas
                broker.adicionarNaFilaConsenso(msg);
            }
        }
        if (responderAgora) broker.enviarMensagemUnica(msg.getIdRemetente(), Mensagem.REPLY, "OK", 0);
    }

    private void enviarACK(Mensagem m) {
        broker.enviarMensagemUnica(m.getIdRemetente(), Mensagem.ACK, m.getMsgId(), 0);
    }
}