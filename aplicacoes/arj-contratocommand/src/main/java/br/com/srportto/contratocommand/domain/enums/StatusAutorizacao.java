package br.com.srportto.contratocommand.domain.enums;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public enum StatusAutorizacao {
    RECEBIDA(1L, false),
    PENDENTE_ATIVACAO(2L, false),
    EM_PROCESSO_ATIVACAO(3L, false),
    ATIVA(4L, false),
    CANCELADA(5L, true),
    REJEITADA(6L, true),
    EXPIRADA(7L, true),
    FINALIZADA(8L, true);

    private long statusAutorizacao;
    private boolean isFinalizador;

    StatusAutorizacao(long statusAutorizacao, boolean isFinalizador) {
        this.statusAutorizacao = statusAutorizacao;
        this.isFinalizador = isFinalizador;
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
                String.format("Status de autorização %i não conhecido ", statusAutorizacaoId));
    }

    public boolean isStatusFinalizador(Long statusAutorizacaoId) {
        StatusAutorizacao statusEnum = obterStatusEnumPorIdStatus(statusAutorizacaoId);
        return statusEnum.isFinalizador;
    }

}
