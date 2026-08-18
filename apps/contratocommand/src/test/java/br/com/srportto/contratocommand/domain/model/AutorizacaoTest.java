package br.com.srportto.contratocommand.domain.model;

import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
