package br.com.srportto.contratocommand.domain.model;

import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do modelo de dominio Autorizacao")
class AutorizacaoTest {

    @Test
    @DisplayName("inicializaCriacao grava o id recebido e aplica defaults; dataFimVigencia nula vira 9999-12-31")
    void inicializaComDefaults() {
        Autorizacao autorizacao = new Autorizacao();
        autorizacao.setIdUnicoContaContratante(UUID.randomUUID());
        autorizacao.setTipoProduto(TipoProduto.DDA_AUTO);
        UUID idGerado = UUID.randomUUID();

        Autorizacao resultado = autorizacao.inicializaCriacao(idGerado);

        assertEquals(idGerado, resultado.getIdAutorizacao());
        assertEquals((int) StatusAutorizacao.ATIVA.getStatusAutorizacao(), resultado.getStatus());
        assertEquals(LocalDate.now(), resultado.getDataInicioVigencia());
        assertNotNull(resultado.getDataHoraInclusao());
        assertNotNull(resultado.getDataHoraUltimaAtualizacao());
        assertEquals((short) 0, resultado.getIndicadorTipoMensageria());
        assertEquals(LocalDate.of(9999, 12, 31), resultado.getDataFimVigencia());
    }

    @Test
    @DisplayName("inicializaCriacao preserva dataFimVigencia já informada")
    void preservaDataFimInformada() {
        Autorizacao autorizacao = new Autorizacao();
        autorizacao.setIdUnicoContaContratante(UUID.randomUUID());
        autorizacao.setTipoProduto(TipoProduto.DDA_AUTO);
        LocalDate fim = LocalDate.of(2030, 1, 1);
        autorizacao.setDataFimVigencia(fim);

        autorizacao.inicializaCriacao(UUID.randomUUID());

        assertEquals(fim, autorizacao.getDataFimVigencia());
    }

    @Test
    @DisplayName("inicializaCriacao grava status RECEBIDA para PIX_AUTO")
    void inicializaComPixAutoGravaRecebida() {
        Autorizacao autorizacao = new Autorizacao();
        autorizacao.setIdUnicoContaContratante(UUID.randomUUID());
        autorizacao.setTipoProduto(TipoProduto.PIX_AUTO);

        Autorizacao resultado = autorizacao.inicializaCriacao(UUID.randomUUID());

        assertEquals((int) StatusAutorizacao.RECEBIDA.getStatusAutorizacao(), resultado.getStatus());
    }

    @Test
    @DisplayName("inicializaCriacao grava status ATIVA para DDA_AUTO")
    void inicializaComDdaAutoGravaAtiva() {
        Autorizacao autorizacao = new Autorizacao();
        autorizacao.setIdUnicoContaContratante(UUID.randomUUID());
        autorizacao.setTipoProduto(TipoProduto.DDA_AUTO);

        Autorizacao resultado = autorizacao.inicializaCriacao(UUID.randomUUID());

        assertEquals((int) StatusAutorizacao.ATIVA.getStatusAutorizacao(), resultado.getStatus());
    }

    @Test
    @DisplayName("inicializaCriacao lança IllegalStateException quando tipoProduto não tem status inicial mapeado")
    void inicializaSemProdutoLancaExcecao() {
        Autorizacao autorizacao = new Autorizacao();
        autorizacao.setIdUnicoContaContratante(UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> autorizacao.inicializaCriacao(UUID.randomUUID()));
    }

    @Test
    @DisplayName("inicializaCriacao normaliza metadados ausente para objeto JSON vazio")
    void metadadosAusenteViraObjetoVazio() {
        Autorizacao autorizacao = new Autorizacao();
        autorizacao.setIdUnicoContaContratante(UUID.randomUUID());
        autorizacao.setTipoProduto(TipoProduto.PIX_AUTO);

        autorizacao.inicializaCriacao(UUID.randomUUID());

        assertEquals("{}", autorizacao.getMetadados());
    }

    @Test
    @DisplayName("inicializaCriacao normaliza metadados em branco para objeto JSON vazio")
    void metadadosEmBrancoViraObjetoVazio() {
        Autorizacao autorizacao = new Autorizacao();
        autorizacao.setIdUnicoContaContratante(UUID.randomUUID());
        autorizacao.setTipoProduto(TipoProduto.PIX_AUTO);
        autorizacao.setMetadados("   ");

        autorizacao.inicializaCriacao(UUID.randomUUID());

        assertEquals("{}", autorizacao.getMetadados());
    }

    @Test
    @DisplayName("inicializaCriacao preserva metadados já informados")
    void preservaMetadadosInformados() {
        Autorizacao autorizacao = new Autorizacao();
        autorizacao.setIdUnicoContaContratante(UUID.randomUUID());
        autorizacao.setTipoProduto(TipoProduto.PIX_AUTO);
        autorizacao.setMetadados("{\"chave\":\"valor\"}");

        autorizacao.inicializaCriacao(UUID.randomUUID());

        assertEquals("{\"chave\":\"valor\"}", autorizacao.getMetadados());
    }

    private Autorizacao autorizacaoAtivaComDados() {
        var aut = new Autorizacao();
        aut.setStatus((int) StatusAutorizacao.ATIVA.getStatusAutorizacao());
        aut.setValorLimite(new BigDecimal("1000.00"));
        aut.setDataFimVigencia(LocalDate.now().plusDays(30));
        aut.setIndicadorUsoLimiteConta((short) 0);
        aut.setQuantidadeDividasCiclo((short) 2);
        return aut;
    }

    @Test
    @DisplayName("atualizarDadosRecorrencia aplica todos os campos informados e atualiza dataHoraUltimaAtualizacao")
    void atualizarDadosRecorrenciaAplicaTodosOsCampos() {
        Autorizacao aut = autorizacaoAtivaComDados();
        var novoLimite = new BigDecimal("5000.00");
        var novaData = LocalDate.now().plusDays(90);

        aut.atualizarDadosRecorrencia(novoLimite, novaData, 1, 5);

        assertEquals(novoLimite, aut.getValorLimite());
        assertEquals(novaData, aut.getDataFimVigencia());
        assertEquals((short) 1, aut.getIndicadorUsoLimiteConta());
        assertEquals((short) 5, aut.getQuantidadeDividasCiclo());
        assertNotNull(aut.getDataHoraUltimaAtualizacao());
    }

    @Test
    @DisplayName("atualizarDadosRecorrencia com campo isolado preserva os demais campos com o valor anterior")
    void atualizarDadosRecorrenciaCampoIsoladoPreservaDemais() {
        Autorizacao aut = autorizacaoAtivaComDados();
        var valorLimiteOriginal = aut.getValorLimite();
        var indicadorOriginal = aut.getIndicadorUsoLimiteConta();
        var quantidadeOriginal = aut.getQuantidadeDividasCiclo();
        var novaData = LocalDate.now().plusDays(90);

        aut.atualizarDadosRecorrencia(null, novaData, null, null);

        assertEquals(novaData, aut.getDataFimVigencia());
        assertEquals(valorLimiteOriginal, aut.getValorLimite());
        assertEquals(indicadorOriginal, aut.getIndicadorUsoLimiteConta());
        assertEquals(quantidadeOriginal, aut.getQuantidadeDividasCiclo());
    }

    @Test
    @DisplayName("atualizarDadosRecorrencia com todos os campos nulos não altera nenhum dado")
    void atualizarDadosRecorrenciaTodosNulosNaoAlteraNada() {
        Autorizacao aut = autorizacaoAtivaComDados();
        var valorLimiteOriginal = aut.getValorLimite();
        var dataFimVigenciaOriginal = aut.getDataFimVigencia();
        var indicadorOriginal = aut.getIndicadorUsoLimiteConta();
        var quantidadeOriginal = aut.getQuantidadeDividasCiclo();

        aut.atualizarDadosRecorrencia(null, null, null, null);

        assertEquals(valorLimiteOriginal, aut.getValorLimite());
        assertEquals(dataFimVigenciaOriginal, aut.getDataFimVigencia());
        assertEquals(indicadorOriginal, aut.getIndicadorUsoLimiteConta());
        assertEquals(quantidadeOriginal, aut.getQuantidadeDividasCiclo());
        assertNotNull(aut.getDataHoraUltimaAtualizacao());
    }

    @Test
    @DisplayName("atualizarDadosRecorrencia não transiciona status")
    void atualizarDadosRecorrenciaNaoTransicionaStatus() {
        Autorizacao aut = autorizacaoAtivaComDados();

        aut.atualizarDadosRecorrencia(new BigDecimal("2000.00"), null, null, null);

        assertEquals((int) StatusAutorizacao.ATIVA.getStatusAutorizacao(), aut.getStatus());
    }
}
