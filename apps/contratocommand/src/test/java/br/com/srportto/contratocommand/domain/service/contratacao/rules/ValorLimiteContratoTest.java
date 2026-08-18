package br.com.srportto.contratocommand.domain.service.contratacao.rules;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Testes da regra ValorLimiteContrato")
class ValorLimiteContratoTest {

    private final ValorLimiteContrato regra = new ValorLimiteContrato();

    @Test
    @DisplayName("PIX_AUTO dentro do limite passa, acima de 1.000.000 lança")
    void pixAuto() {
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarContext(
                TipoProduto.PIX_AUTO, new BigDecimal("1000000"), LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
        assertThrows(BusinessException.class, () -> regra.validar(TestFixtures.criarContext(
                TipoProduto.PIX_AUTO, new BigDecimal("1000000.01"), LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
    }

    @Test
    @DisplayName("DDA_AUTO dentro do limite passa, acima de 250.000 lança")
    void ddaAuto() {
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarContext(
                TipoProduto.DDA_AUTO, new BigDecimal("250000"), LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
        assertThrows(BusinessException.class, () -> regra.validar(TestFixtures.criarContext(
                TipoProduto.DDA_AUTO, new BigDecimal("250000.01"), LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
    }

    @Test
    @DisplayName("valor zero e valor negativo sao rejeitados")
    void valorNaoPositivoLanca() {
        assertThrows(BusinessException.class, () -> regra.validar(TestFixtures.criarContext(
                TipoProduto.PIX_AUTO, BigDecimal.ZERO, LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
        assertThrows(BusinessException.class, () -> regra.validar(TestFixtures.criarContext(
                TipoProduto.PIX_AUTO, new BigDecimal("-0.01"), LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
    }
}
