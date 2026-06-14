package br.com.srportto.contratoquery.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do enum MotivoStatusAutorizacao")
class MotivoStatusAutorizacaoTest {

    @Test
    @DisplayName("obterMotivoStatusEnumPorIdMotivo resolve todos os códigos e expõe descrição")
    void obtemPorIdParaTodos() {
        for (MotivoStatusAutorizacao motivo : MotivoStatusAutorizacao.values()) {
            MotivoStatusAutorizacao encontrado =
                    MotivoStatusAutorizacao.obterMotivoStatusEnumPorIdMotivo(motivo.getCodigoMotivo());
            assertEquals(motivo, encontrado);
            assertNotNull(encontrado.getDescricao());
        }
    }

    @Test
    @DisplayName("obterMotivoStatusEnumPorIdMotivo lança IllegalArgumentException para código desconhecido")
    void lancaParaDesconhecido() {
        assertThrows(IllegalArgumentException.class,
                () -> MotivoStatusAutorizacao.obterMotivoStatusEnumPorIdMotivo(9999L));
    }

    @Test
    @DisplayName("getCodigoMotivo expõe o código numérico")
    void exposeCodigo() {
        assertEquals(1L, MotivoStatusAutorizacao.RECEPCAO_SPI_J1.getCodigoMotivo());
        assertEquals(21L, MotivoStatusAutorizacao.FINALIZADA_01.getCodigoMotivo());
    }
}
