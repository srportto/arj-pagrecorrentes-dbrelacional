package br.com.srportto.autorizacaostatusproducer.domain.enums;

/**
 * Espelho manual do status persistido pelo contratocommand. Esta app é uma ponte de formatos
 * (SQS → Kafka), sem regra de máquina de estados própria — só precisa mapear id↔enum para
 * derivar {@link TipoEventoAutorizacao}.
 */
public enum StatusAutorizacao {
    RECEBIDA(1L),
    PENDENTE_ACEITE(2L),
    EM_PROCESSO_ATIVACAO(3L),
    ATIVA(4L),
    CANCELADA(5L),
    REJEITADA(6L),
    EXPIRADA(7L),
    FINALIZADA(8L);

    private final long statusAutorizacao;

    StatusAutorizacao(long statusAutorizacao) {
        this.statusAutorizacao = statusAutorizacao;
    }

    public long getStatusAutorizacao() {
        return this.statusAutorizacao;
    }

    public static StatusAutorizacao obterStatusEnumPorIdStatus(long statusAutorizacaoId) {
        for (StatusAutorizacao statusEnum : StatusAutorizacao.values()) {
            if (statusEnum.getStatusAutorizacao() == statusAutorizacaoId) {
                return statusEnum;
            }
        }
        throw new IllegalArgumentException(
                String.format("Status de autorização %d não conhecido ", statusAutorizacaoId));
    }
}
