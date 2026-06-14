package br.com.srportto.contratocommand.domain.entities;

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

        Autorizacao resultado = autorizacao.inicializaCriacao(autorizacao);

        assertNotNull(resultado.getIdAutorizacao());
        assertNotNull(resultado.getIdAutorizacao().getIdAutorizacao());
        int particao = resultado.getIdAutorizacao().getIdParticaoConta();
        assertTrue(particao >= 0 && particao < 889, "partição embutida deve estar em 0..888, foi " + particao);

        assertEquals(1, resultado.getStatus());
        assertNotNull(resultado.getMotivoStatus());
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
        LocalDate fim = LocalDate.of(2030, 1, 1);
        autorizacao.setDataFimVigencia(fim);

        autorizacao.inicializaCriacao(autorizacao);

        assertEquals(fim, autorizacao.getDataFimVigencia());
    }
}
