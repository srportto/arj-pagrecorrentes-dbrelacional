package br.com.srportto.contratoquery.domain.enums;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public enum StatusAutorizacao {
    RECEBIDA(1L),
    PENDENTE_ACEITE(2L),
    EM_PROCESSO_ATIVACAO(3L),
    ATIVA(4L),
    CANCELADA(5L),
    REJEITADA(6L),
    EXPIRADA(7L),
    FINALIZADA(8L);

    private long statusAutorizacao;

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
