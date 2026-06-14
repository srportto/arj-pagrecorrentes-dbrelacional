package br.com.srportto.contratoquery.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do enum StatusAutorizacao")
class StatusAutorizacaoTest {

    @Test
    @DisplayName("obterStatusEnumPorIdStatus retorna o enum correspondente para cada código")
    void obtemEnumPorIdParaTodosOsCodigos() {
        for (StatusAutorizacao status : StatusAutorizacao.values()) {
            StatusAutorizacao encontrado =
                    StatusAutorizacao.obterStatusEnumPorIdStatus(status.getStatusAutorizacao());
            assertEquals(status, encontrado);
        }
    }

    @Test
    @DisplayName("obterStatusEnumPorIdStatus lança IllegalArgumentException para código desconhecido")
    void lancaParaCodigoDesconhecido() {
        assertThrows(IllegalArgumentException.class,
                () -> StatusAutorizacao.obterStatusEnumPorIdStatus(999L));
    }

    @Test
    @DisplayName("isStatusFinalizador distingue status finais de não-finais")
    void identificaStatusFinalizador() {
        assertTrue(StatusAutorizacao.CANCELADA.isStatusFinalizador(StatusAutorizacao.CANCELADA.getStatusAutorizacao()));
        assertTrue(StatusAutorizacao.EXPIRADA.isStatusFinalizador(StatusAutorizacao.EXPIRADA.getStatusAutorizacao()));
        assertFalse(StatusAutorizacao.ATIVA.isStatusFinalizador(StatusAutorizacao.ATIVA.getStatusAutorizacao()));
        assertFalse(StatusAutorizacao.RECEBIDA.isStatusFinalizador(StatusAutorizacao.RECEBIDA.getStatusAutorizacao()));
    }

    @Test
    @DisplayName("getStatusAutorizacao expõe o código numérico")
    void exposeCodigo() {
        assertEquals(4L, StatusAutorizacao.ATIVA.getStatusAutorizacao());
        assertEquals(1L, StatusAutorizacao.RECEBIDA.getStatusAutorizacao());
    }
}
