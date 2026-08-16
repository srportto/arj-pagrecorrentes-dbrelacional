package br.com.srportto.contratocommand.domain.enums;

import br.com.srportto.contratocommand.domain.exception.BusinessException;

/** Ação submetida via {@code PATCH /api/autorizacoes/{id}/decisao} sobre uma autorização em RECEBIDA. */
public enum AcaoDecisao {
    APROVAR,
    REJEITAR,
    EXPIRAR;

    public static AcaoDecisao obterAcaoDecisaoEnumPorNome(String nome) {
        for (AcaoDecisao acao : AcaoDecisao.values()) {
            if (acao.name().equalsIgnoreCase(nome)) {
                return acao;
            }
        }
        throw new BusinessException(String.format("Ação de decisão '%s' não conhecida", nome));
    }
}
