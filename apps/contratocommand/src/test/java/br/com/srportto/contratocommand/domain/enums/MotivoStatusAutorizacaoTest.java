package br.com.srportto.contratocommand.domain.enums;

import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do enum MotivoStatusAutorizacao")
class MotivoStatusAutorizacaoTest {

    @Test
    @DisplayName("obterJornadaAutorizacaoEnumPorIdJornada resolve todos os códigos e expõe descrição")
    void obtemPorIdParaTodos() {
        for (MotivoStatusAutorizacao motivo : MotivoStatusAutorizacao.values()) {
            MotivoStatusAutorizacao encontrado =
                    MotivoStatusAutorizacao.obterMotivoStatusEnumPorIdMotivo(motivo.getCodigoMotivo());
            assertEquals(motivo, encontrado);
            assertNotNull(encontrado.getDescricao());
        }
    }

    @Test
    @DisplayName("obterJornadaAutorizacaoEnumPorIdJornada lança BusinessException para código desconhecido")
    void lancaParaDesconhecido() {
        assertThrows(BusinessException.class,
                () -> MotivoStatusAutorizacao.obterMotivoStatusEnumPorIdMotivo(9999L));
    }

    @Test
    @DisplayName("getCodigoMotivo expõe o código numérico")
    void exposeCodigo() {
        assertEquals(1L, MotivoStatusAutorizacao.RECEPCAO_SPI_J1.getCodigoMotivo());
        assertEquals(24L, MotivoStatusAutorizacao.FINALIZADA_01.getCodigoMotivo());
        assertEquals(25L, MotivoStatusAutorizacao.REJEITADA_SISTEMA_TIMEOUT_J1.getCodigoMotivo());
    }

    @Test
    @DisplayName("nenhum código de motivo colide com outro")
    void codigosSaoUnicos() {
        var codigos = new java.util.HashSet<Long>();
        for (MotivoStatusAutorizacao motivo : MotivoStatusAutorizacao.values()) {
            assertTrue(codigos.add(motivo.getCodigoMotivo()),
                    "Código duplicado para " + motivo);
        }
    }

    @Test
    @DisplayName("REJEITADA_SISTEMA_TIMEOUT_J1 é distinto de REJEITADA_PAGADOR")
    void timeoutSistemaDistintoDeRejeicaoCliente() {
        assertNotEquals(MotivoStatusAutorizacao.REJEITADA_PAGADOR,
                MotivoStatusAutorizacao.REJEITADA_SISTEMA_TIMEOUT_J1);
        assertNotEquals(MotivoStatusAutorizacao.REJEITADA_PAGADOR.getCodigoMotivo(),
                MotivoStatusAutorizacao.REJEITADA_SISTEMA_TIMEOUT_J1.getCodigoMotivo());
    }
}
