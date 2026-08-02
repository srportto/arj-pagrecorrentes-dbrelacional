package br.com.srportto.autorizacaostatusproducer.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do enum StatusAutorizacao")
class StatusAutorizacaoTest {

    @Test
    @DisplayName("obterStatusEnumPorIdStatus retorna o enum correspondente para cada código")
    void obtemEnumPorIdParaTodosOsCodigos() {
        for (StatusAutorizacao status : StatusAutorizacao.values()) {
            assertEquals(status, StatusAutorizacao.obterStatusEnumPorIdStatus(status.getStatusAutorizacao()));
        }
    }

    @Test
    @DisplayName("obterStatusEnumPorIdStatus lança IllegalArgumentException para código desconhecido")
    void lancaParaCodigoDesconhecido() {
        assertThrows(IllegalArgumentException.class, () -> StatusAutorizacao.obterStatusEnumPorIdStatus(999L));
    }

    @Test
    @DisplayName("ATIVA pode transicionar para CANCELADA, FINALIZADA e REJEITADA, mas não EXPIRADA")
    void transicoesDeAtiva() {
        assertTrue(StatusAutorizacao.ATIVA.podeTransicionarPara(StatusAutorizacao.CANCELADA));
        assertTrue(StatusAutorizacao.ATIVA.podeTransicionarPara(StatusAutorizacao.FINALIZADA));
        assertTrue(StatusAutorizacao.ATIVA.podeTransicionarPara(StatusAutorizacao.REJEITADA));
        assertFalse(StatusAutorizacao.ATIVA.podeTransicionarPara(StatusAutorizacao.EXPIRADA));
    }

    @Test
    @DisplayName("estados terminais não transicionam para nenhum outro estado")
    void estadosTerminaisNaoTransicionam() {
        for (StatusAutorizacao terminal : new StatusAutorizacao[] {
                StatusAutorizacao.CANCELADA, StatusAutorizacao.REJEITADA,
                StatusAutorizacao.EXPIRADA, StatusAutorizacao.FINALIZADA}) {
            for (StatusAutorizacao destino : StatusAutorizacao.values()) {
                assertFalse(terminal.podeTransicionarPara(destino));
            }
        }
    }
}
