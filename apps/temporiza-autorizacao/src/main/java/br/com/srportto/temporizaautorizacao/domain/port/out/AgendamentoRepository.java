package br.com.srportto.temporizaautorizacao.domain.port.out;

import java.time.Instant;
import java.util.UUID;

/** Porta de saída do agendamento: relógio de vencimentos (sorted set no Valkey). */
public interface AgendamentoRepository {

    /** Agenda (ou reagenda) o vencimento. Idempotente: reagendar o mesmo id só sobrescreve o score. */
    void agendar(UUID idAutorizacao, Instant vencimento);

    /**
     * Move atomicamente os agendamentos vencidos até {@code agora} (limitado a {@code lote}) do
     * relógio para o stream de expirações. A atomicidade (e a eleição de qual instância processa
     * cada id, sem lock distribuído) é responsabilidade exclusiva do adaptador — o caso de uso não
     * sabe que existe script Lua, sorted set ou stream por trás desta chamada.
     *
     * @return quantidade de agendamentos movidos
     */
    int moverVencidosParaExpiracao(Instant agora, int lote);

}
