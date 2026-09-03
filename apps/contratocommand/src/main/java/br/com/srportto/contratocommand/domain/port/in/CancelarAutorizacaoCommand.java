package br.com.srportto.contratocommand.domain.port.in;

import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.model.AutorizacaoId;

import java.util.UUID;

/**
 * Comando de cancelamento (path + header + corpo). {@code tipoProdutoAutorizacao} e
 * {@code statusAtual} são preenchidos pelo caso de uso via {@link #comAutorizacaoCarregada},
 * sem mutar o comando original. Espelha {@code DecidirAutorizacaoCommand}. O identificador já
 * chega validado como {@link AutorizacaoId} — a conversão de formato acontece na borda, não aqui.
 */
public record CancelarAutorizacaoCommand(
        AutorizacaoId idAutorizacao,
        TipoProduto tipoProduto,
        TipoProduto tipoProdutoAutorizacao,
        StatusAutorizacao statusAtual,
        String codigoCanalCancelamento,
        UUID idPessoaCancelamento,
        String motivoCancelamento) {

    public static CancelarAutorizacaoCommand doRequest(AutorizacaoId idAutorizacao, TipoProduto tipoProduto,
            String codigoCanalCancelamento, UUID idPessoaCancelamento, String motivoCancelamento) {
        return new CancelarAutorizacaoCommand(idAutorizacao, tipoProduto, null, null,
                codigoCanalCancelamento, idPessoaCancelamento, motivoCancelamento);
    }

    public CancelarAutorizacaoCommand comAutorizacaoCarregada(TipoProduto tipoProdutoAutorizacao, StatusAutorizacao statusAtual) {
        return new CancelarAutorizacaoCommand(idAutorizacao, tipoProduto, tipoProdutoAutorizacao, statusAtual,
                codigoCanalCancelamento, idPessoaCancelamento, motivoCancelamento);
    }
}
