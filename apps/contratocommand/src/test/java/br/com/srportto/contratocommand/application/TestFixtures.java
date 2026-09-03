package br.com.srportto.contratocommand.application;

import br.com.srportto.contratocommand.domain.model.AutorizacaoId;
import br.com.srportto.contratocommand.domain.port.in.AtualizarDadosRecorrenciaCommand;
import br.com.srportto.contratocommand.domain.port.in.CancelarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.infrastructure.web.contratosrest.AtualizarDadosRecorrenciaRequest;
import br.com.srportto.contratocommand.infrastructure.web.contratosrest.CancelarAutorizacaoRequest;
import br.com.srportto.contratocommand.infrastructure.web.contratosrest.CriarAutorizacaoRequest;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fábricas de objetos para os testes unitários do contratocommand.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static CriarAutorizacaoRequest criarRequest(String tipoProduto, BigDecimal valor,
            LocalDate dataFimVigencia, JsonNode metadados) {
        return new CriarAutorizacaoRequest(
                dataFimVigencia,
                tipoProduto,
                valor,
                "EMP001",
                new BigDecimal("2000.00"),
                2,
                2,
                0,
                "C1",
                "descricao de teste",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                metadados);
    }

    public static CriarAutorizacaoRequest criarRequestPix() {
        return criarRequest("PIX_AUTO", new BigDecimal("1000.00"), LocalDate.now().plusDays(30), null);
    }

    public static CriarAutorizacaoRequest criarRequestDda() {
        return criarRequest("DDA_AUTO", new BigDecimal("1000.00"), LocalDate.now().plusDays(30), null);
    }

    /**
     * Comando de criação já com {@code tipoProduto} resolvido — a resolução de string→enum
     * (e a rejeição de nome desconhecido) acontece no controller, não faz parte do comando.
     * Ver {@code TipoProdutoTest} para a cobertura de resolução, e
     * {@code AutorizacaoControllerTest} para a cobertura do controller rejeitando antes do use case.
     */
    public static CriarAutorizacaoCommand criarContext(TipoProduto tipoProduto, BigDecimal valor,
            LocalDate dataFimVigencia, JsonNode metadados, TipoJornadaAutorizacao tipoJornada) {
        return new CriarAutorizacaoCommand(
                tipoJornada,
                dataFimVigencia,
                tipoProduto,
                valor,
                "EMP001",
                new BigDecimal("2000.00"),
                2,
                2,
                0,
                "C1",
                "descricao de teste",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                metadados == null ? null : metadados.toString());
    }

    public static CriarAutorizacaoCommand criarContextPix() {
        return criarContext(TipoProduto.PIX_AUTO, new BigDecimal("1000.00"), LocalDate.now().plusDays(30), null,
                TipoJornadaAutorizacao.SPI_J1);
    }

    public static CriarAutorizacaoCommand criarContextDda() {
        return criarContext(TipoProduto.DDA_AUTO, new BigDecimal("1000.00"), LocalDate.now().plusDays(30), null,
                TipoJornadaAutorizacao.SPI_J1);
    }

    public static CancelarAutorizacaoRequest cancelarDados() {
        return new CancelarAutorizacaoRequest("C1", UUID.randomUUID(), "teste cancelamento");
    }

    /** Aceita {@code String} (não {@link AutorizacaoId}) para preservar os call sites existentes
     * dos testes de regra, que passam qualquer id sintático — a validação de formato UUID
     * acontece aqui, na borda simulada pelo fixture (espelha {@code AutorizacaoController}). */
    public static CancelarAutorizacaoCommand cancelarContext(String idAutorizacao, TipoProduto produtoHeader) {
        CancelarAutorizacaoRequest dados = cancelarDados();
        return CancelarAutorizacaoCommand.doRequest(AutorizacaoId.de(idAutorizacao), produtoHeader,
                dados.codigoCanalCancelamento(), dados.idPessoaCancelamento(), dados.motivoCancelamento());
    }

    public static AtualizarDadosRecorrenciaRequest atualizarDados() {
        return new AtualizarDadosRecorrenciaRequest(
                new BigDecimal("3000.00"), LocalDate.now().plusDays(60), 1, 3, "C1", UUID.randomUUID());
    }

    public static AtualizarDadosRecorrenciaCommand atualizarContext(String idAutorizacao, TipoProduto produtoHeader) {
        AtualizarDadosRecorrenciaRequest dados = atualizarDados();
        return AtualizarDadosRecorrenciaCommand.doRequest(AutorizacaoId.de(idAutorizacao), produtoHeader,
                dados.valorLimite(), dados.dataFimVigencia(), dados.indicadorUsoLimiteConta(),
                dados.quantidadeDividasCiclo(), dados.codigoCanalAtualizacao(), dados.idPessoaAtualizacao());
    }
}
