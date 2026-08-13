package br.com.srportto.temporizaautorizacao.application.agendamento;

import java.time.Instant;
import java.util.UUID;

/** Porta de saída do agendamento: relógio de vencimentos (sorted set no Valkey). */
public interface AgendamentoRepository {

    /** Agenda (ou reagenda) o vencimento. Idempotente: reagendar o mesmo id só sobrescreve o score. */
    void agendar(UUID idAutorizacao, Instant vencimento);

}
