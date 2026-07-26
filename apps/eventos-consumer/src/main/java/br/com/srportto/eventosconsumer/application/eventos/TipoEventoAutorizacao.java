package br.com.srportto.eventosconsumer.application.eventos;

/** Tipo do evento, em bijeção com {@link StatusAutorizacao} — derivado do status, nunca informado à parte. */
public enum TipoEventoAutorizacao {
    RECEPCAO(StatusAutorizacao.RECEBIDA),
    PENDENCIA_ACEITE(StatusAutorizacao.PENDENTE_ACEITE),
    INICIO_ATIVACAO(StatusAutorizacao.EM_PROCESSO_ATIVACAO),
    ATIVACAO(StatusAutorizacao.ATIVA),
    CANCELAMENTO(StatusAutorizacao.CANCELADA),
    REJEICAO(StatusAutorizacao.REJEITADA),
    EXPIRACAO(StatusAutorizacao.EXPIRADA),
    FINALIZACAO(StatusAutorizacao.FINALIZADA);

    private final StatusAutorizacao status;

    TipoEventoAutorizacao(StatusAutorizacao status) {
        this.status = status;
    }

    public static TipoEventoAutorizacao porStatus(long statusId) {
        StatusAutorizacao status = StatusAutorizacao.obterStatusEnumPorIdStatus(statusId);
        for (TipoEventoAutorizacao tipo : values()) {
            if (tipo.status == status) {
                return tipo;
            }
        }
        throw new IllegalArgumentException(
                String.format("Nenhum tipo de evento mapeado para o status %s", status));
    }
}
