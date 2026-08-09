package br.com.srportto.contratocommand.application.decisao;

import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.DecisaoAutorizacaoRequest;

/**
 * Contexto imutável da decisão, montado a partir do path ({@code idAutorizacao}), do header
 * ({@code tipoProduto}) e do corpo ({@code dados}). Espelha {@code CancelamentoContext}: os dados
 * lidos do banco ({@code tipoProdutoAutorizacao}, {@code statusAtual}) são preenchidos pelo caso
 * de uso via {@link #comAutorizacaoCarregada} antes da validação, sem mutar nenhum DTO de request.
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
