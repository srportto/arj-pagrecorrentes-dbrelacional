package br.com.srportto.contratocommand.domain.port.in;

import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;

import java.util.UUID;

/**
 * Comando de cancelamento (path + header + corpo). {@code tipoProdutoAutorizacao} e
 * {@code statusAtual} são preenchidos pelo caso de uso via {@link #comAutorizacaoCarregada},
 * sem mutar o comando original. Espelha {@code DecidirAutorizacaoCommand}.
 */
public record CancelarAutorizacaoCommand(
        String idAutorizacao,
        TipoProduto tipoProduto,
        TipoProduto tipoProdutoAutorizacao,
        StatusAutorizacao statusAtual,
        String codigoCanalCancelamento,
        UUID idPessoaCancelamento,
        String motivoCancelamento) {

    public static CancelarAutorizacaoCommand doRequest(String idAutorizacao, TipoProduto tipoProduto,
            String codigoCanalCancelamento, UUID idPessoaCancelamento, String motivoCancelamento) {
        return new CancelarAutorizacaoCommand(idAutorizacao, tipoProduto, null, null,
                codigoCanalCancelamento, idPessoaCancelamento, motivoCancelamento);
    }

    public CancelarAutorizacaoCommand comAutorizacaoCarregada(TipoProduto tipoProdutoAutorizacao, StatusAutorizacao statusAtual) {
        return new CancelarAutorizacaoCommand(idAutorizacao, tipoProduto, tipoProdutoAutorizacao, statusAtual,
                codigoCanalCancelamento, idPessoaCancelamento, motivoCancelamento);
    }
}
