package br.com.srportto.contratocommand.application.decisao;

import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.DecisaoAutorizacaoRequest;

/**
 * Contexto imutável da decisão (path + header + corpo). Espelha {@code CancelamentoContext}:
 * dados do banco preenchidos via {@link #comAutorizacaoCarregada}, sem mutar o request.
 */
public record DecisaoContext(
        String idAutorizacao,
        TipoProduto tipoProduto,
        TipoProduto tipoProdutoAutorizacao,
        StatusAutorizacao statusAtual,
        DecisaoAutorizacaoRequest dados) {

    public static DecisaoContext doRequest(String idAutorizacao, TipoProduto tipoProduto,
            DecisaoAutorizacaoRequest dados) {
        return new DecisaoContext(idAutorizacao, tipoProduto, null, null, dados);
    }

    public DecisaoContext comAutorizacaoCarregada(TipoProduto tipoProdutoAutorizacao, StatusAutorizacao statusAtual) {
        return new DecisaoContext(idAutorizacao, tipoProduto, tipoProdutoAutorizacao, statusAtual, dados);
    }
}
