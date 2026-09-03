package br.com.srportto.contratocommand.domain.port.in;

import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.model.AutorizacaoId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Comando de atualização parcial de dados de uma autorização ATIVA (path + header + corpo).
 * Espelha {@code DecidirAutorizacaoCommand}/{@code CancelarAutorizacaoCommand}: dados do banco
 * preenchidos via {@link #comAutorizacaoCarregada}, sem mutar o comando original. Campo nulo
 * significa "não altera" (ver design.md, D3). O identificador já chega validado como
 * {@link AutorizacaoId}.
 */
public record AtualizarDadosRecorrenciaCommand(
        AutorizacaoId idAutorizacao,
        TipoProduto tipoProduto,
        TipoProduto tipoProdutoAutorizacao,
        StatusAutorizacao statusAtual,
        BigDecimal valorLimite,
        LocalDate dataFimVigencia,
        Integer indicadorUsoLimiteConta,
        Integer quantidadeDividasCiclo,
        String codigoCanalAtualizacao,
        UUID idPessoaAtualizacao) {

    public static AtualizarDadosRecorrenciaCommand doRequest(AutorizacaoId idAutorizacao, TipoProduto tipoProduto,
            BigDecimal valorLimite, LocalDate dataFimVigencia, Integer indicadorUsoLimiteConta,
            Integer quantidadeDividasCiclo, String codigoCanalAtualizacao, UUID idPessoaAtualizacao) {
        return new AtualizarDadosRecorrenciaCommand(idAutorizacao, tipoProduto, null, null,
                valorLimite, dataFimVigencia, indicadorUsoLimiteConta, quantidadeDividasCiclo,
                codigoCanalAtualizacao, idPessoaAtualizacao);
    }

    public AtualizarDadosRecorrenciaCommand comAutorizacaoCarregada(TipoProduto tipoProdutoAutorizacao, StatusAutorizacao statusAtual) {
        return new AtualizarDadosRecorrenciaCommand(idAutorizacao, tipoProduto, tipoProdutoAutorizacao, statusAtual,
                valorLimite, dataFimVigencia, indicadorUsoLimiteConta, quantidadeDividasCiclo,
                codigoCanalAtualizacao, idPessoaAtualizacao);
    }
}
