package br.com.srportto.contratocommand.domain.port.in;

import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.model.AutorizacaoId;

import java.util.UUID;

/**
 * Comando de decisão (path + header + corpo). Espelha {@code CancelarAutorizacaoCommand}: dados
 * do banco preenchidos via {@link #comAutorizacaoCarregada}, sem mutar o comando original. O
 * identificador já chega validado como {@link AutorizacaoId}.
 */
public record DecidirAutorizacaoCommand(
        AutorizacaoId idAutorizacao,
        TipoProduto tipoProduto,
        TipoProduto tipoProdutoAutorizacao,
        StatusAutorizacao statusAtual,
        String acao,
        String codigoCanalDecisao,
        UUID idPessoaDecisao) {

    public static DecidirAutorizacaoCommand doRequest(AutorizacaoId idAutorizacao, TipoProduto tipoProduto,
            String acao, String codigoCanalDecisao, UUID idPessoaDecisao) {
        return new DecidirAutorizacaoCommand(idAutorizacao, tipoProduto, null, null,
                acao, codigoCanalDecisao, idPessoaDecisao);
    }

    public DecidirAutorizacaoCommand comAutorizacaoCarregada(TipoProduto tipoProdutoAutorizacao, StatusAutorizacao statusAtual) {
        return new DecidirAutorizacaoCommand(idAutorizacao, tipoProduto, tipoProdutoAutorizacao, statusAtual,
                acao, codigoCanalDecisao, idPessoaDecisao);
    }
}
