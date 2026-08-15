package br.com.srportto.contratocommand.application.cancelamento;

import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequest;

/**
 * Contexto imutável do cancelamento (path + header + corpo). {@code tipoProdutoAutorizacao} e
 * {@code statusAtual} são preenchidos pelo caso de uso via {@link #comAutorizacaoCarregada},
 * sem mutar o request. Espelha {@code DecisaoContext}.
 */
public record CancelamentoContext(
        String idAutorizacao,
        TipoProduto tipoProduto,
        TipoProduto tipoProdutoAutorizacao,
        StatusAutorizacao statusAtual,
        CancelarAutorizacaoRequest dados) {

    public static CancelamentoContext doRequest(String idAutorizacao, TipoProduto tipoProduto,
            CancelarAutorizacaoRequest dados) {
        return new CancelamentoContext(idAutorizacao, tipoProduto, null, null, dados);
    }

    public CancelamentoContext comAutorizacaoCarregada(TipoProduto tipoProdutoAutorizacao, StatusAutorizacao statusAtual) {
        return new CancelamentoContext(idAutorizacao, tipoProduto, tipoProdutoAutorizacao, statusAtual, dados);
    }
}
