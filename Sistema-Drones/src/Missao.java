import java.io.Serializable;

/**
 * Entidade de Missão.
 * Implementa a interface Comparable para ordenar a fila por prioridade efetiva.
 * Utiliza o conceito de 'Aging' (envelhecimento) para evitar starvation de missões menos urgentes.
 */
public class Missao implements Comparable<Missao>, Serializable {
    private static final long serialVersionUID = 1L;

    public String idUnico;
    public int prioridadeBase;
    public long instanteCriacao;
    public int timestampLamport;

    public Missao(String idUnico, int prioridade, int timestampLamport) {
        this.idUnico = idUnico;
        this.prioridadeBase = prioridade;
        this.timestampLamport = timestampLamport;
        this.instanteCriacao = System.currentTimeMillis();
    }

    /**
     * Calcula a prioridade dinâmica da missão.
     * Aplica um bônus a cada 15 segundos de espera na fila, garantindo que
     * missões antigas eventualmente subam na fila de prioridade.
     */
    public int getPrioridadeEfetiva() {
        long esperaSegundos = (System.currentTimeMillis() - instanteCriacao) / 1000;
        int bonusAging = (int) (esperaSegundos / 15);
        return prioridadeBase + bonusAging;
    }

    /**
     * Critério de desempate e ordenação da fila:
     * 1. Maior prioridade efetiva.
     * 2. Menor timestamp de Lamport (ordem causal).
     * 3. ID único da missão em caso de colisão total.
     */
    @Override
    public int compareTo(Missao outra) {
        int p1 = this.getPrioridadeEfetiva();
        int p2 = outra.getPrioridadeEfetiva();

        if (p1 != p2) return Integer.compare(p2, p1);
        if (this.timestampLamport != outra.timestampLamport)
            return Integer.compare(this.timestampLamport, outra.timestampLamport);
        return this.idUnico.compareTo(outra.idUnico);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Missao)) return false;
        return idUnico.equals(((Missao) o).idUnico);
    }

    @Override
    public int hashCode() { return idUnico.hashCode(); }
}