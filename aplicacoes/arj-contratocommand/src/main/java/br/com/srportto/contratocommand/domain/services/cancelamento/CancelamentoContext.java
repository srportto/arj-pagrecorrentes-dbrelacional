package br.com.srportto.contratocommand.domain.services.cancelamento;

import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequest;

/**
 * Contexto imutável do cancelamento, montado a partir do path ({@code idAutorizacao}), do header
 * ({@code tipoProduto}) e do corpo ({@code dados}). É também o payload de validação: o
 * {@code tipoProdutoAutorizacao} (produto realmente atrelado ao id, lido do banco) é preenchido
 * pelo caso de uso via {@link #comProdutoAutorizacao(TipoProduto)} antes da validação, sem mutar
 * nenhum DTO de request.
 */
public record CancelamentoContext(
        String idAutorizacao,
        TipoProduto tipoProduto,
        TipoProduto tipoProdutoAutorizacao,
        CancelarAutorizacaoRequest dados) {

    public static CancelamentoContext doRequest(String idAutorizacao, TipoProduto tipoProduto,
            CancelarAutorizacaoRequest dados) {
        return new CancelamentoContext(idAutorizacao, tipoProduto, null, dados);
    }

    public CancelamentoContext comProdutoAutorizacao(TipoProduto tipoProdutoAutorizacao) {
        return new CancelamentoContext(idAutorizacao, tipoProduto, tipoProdutoAutorizacao, dados);
    }
}
