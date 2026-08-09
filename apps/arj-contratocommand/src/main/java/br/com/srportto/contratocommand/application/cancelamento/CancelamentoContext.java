package br.com.srportto.contratocommand.application.cancelamento;

import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequest;

/**
 * Contexto imutável do cancelamento, montado a partir do path ({@code idAutorizacao}), do header
 * ({@code tipoProduto}) e do corpo ({@code dados}). É também o payload de validação: o
 * {@code tipoProdutoAutorizacao} (produto realmente atrelado ao id, lido do banco) e o
 * {@code statusAtual} são preenchidos pelo caso de uso via {@link #comAutorizacaoCarregada}
 * antes da validação, sem mutar nenhum DTO de request. Espelha {@code DecisaoContext}.
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
