package br.com.srportto.temporizaautorizacao.application.agendamento;

import java.time.Instant;
import java.util.UUID;

/** Porta de saída do agendamento: relógio de vencimentos (sorted set no Valkey). */
public interface AgendamentoRepository {

    /**
     * Agenda (ou reagenda, se já existir) o vencimento da autorização. Idempotente por
     * natureza: reagendar o mesmo id apenas sobrescreve o score anterior.
     */
    void agendar(UUID idAutorizacao, Instant vencimento);

}
