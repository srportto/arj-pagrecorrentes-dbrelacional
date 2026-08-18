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

}
