package br.com.srportto.contratocommand.domain.entities;

import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes da entidade Autorizacao")
class AutorizacaoTest {

    @Test
    @DisplayName("inicializaCriacao gera id/partição e aplica defaults; dataFimVigencia nula vira 9999-12-31")
    void inicializaComDefaults() {
        Autorizacao autorizacao = new Autorizacao();
        autorizacao.setIdUnicoContaContratante(UUID.randomUUID());
        autorizacao.setTipoProduto(TipoProduto.DDA_AUTO);

        Autorizacao resultado = autorizacao.inicializaCriacao();

        assertNotNull(resultado.getIdAutorizacao());
        assertNotNull(resultado.getIdAutorizacao().getIdAutorizacao());
        int particao = resultado.getIdAutorizacao().getIdParticaoConta();
        assertTrue(particao >= 0 && particao < 889, "partição embutida deve estar em 0..888, foi " + particao);

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

        autorizacao.inicializaCriacao();

        assertEquals(fim, autorizacao.getDataFimVigencia());
    }

    @Test
    @DisplayName("inicializaCriacao grava status RECEBIDA para PIX_AUTO")
    void inicializaComPixAutoGravaRecebida() {
        Autorizacao autorizacao = new Autorizacao();
        autorizacao.setIdUnicoContaContratante(UUID.randomUUID());
        autorizacao.setTipoProduto(TipoProduto.PIX_AUTO);

        Autorizacao resultado = autorizacao.inicializaCriacao();

        assertEquals((int) StatusAutorizacao.RECEBIDA.getStatusAutorizacao(), resultado.getStatus());
    }

    @Test
    @DisplayName("inicializaCriacao grava status ATIVA para DDA_AUTO")
    void inicializaComDdaAutoGravaAtiva() {
        Autorizacao autorizacao = new Autorizacao();
        autorizacao.setIdUnicoContaContratante(UUID.randomUUID());
        autorizacao.setTipoProduto(TipoProduto.DDA_AUTO);

        Autorizacao resultado = autorizacao.inicializaCriacao();

        assertEquals((int) StatusAutorizacao.ATIVA.getStatusAutorizacao(), resultado.getStatus());
    }

    @Test
    @DisplayName("inicializaCriacao lança IllegalStateException quando tipoProduto não tem status inicial mapeado")
    void inicializaSemProdutoLancaExcecao() {
        Autorizacao autorizacao = new Autorizacao();
        autorizacao.setIdUnicoContaContratante(UUID.randomUUID());

        assertThrows(IllegalStateException.class, autorizacao::inicializaCriacao);
    }
}
