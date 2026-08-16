package br.com.srportto.contratocommand.domain.service.contratacao.rules;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
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
                "PIX_AUTO", new BigDecimal("1000000"), LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
        assertThrows(BusinessException.class, () -> regra.validar(TestFixtures.criarContext(
                "PIX_AUTO", new BigDecimal("1000000.01"), LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
    }

    @Test
    @DisplayName("DDA_AUTO dentro do limite passa, acima de 250.000 lança")
    void ddaAuto() {
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarContext(
                "DDA_AUTO", new BigDecimal("250000"), LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
        assertThrows(BusinessException.class, () -> regra.validar(TestFixtures.criarContext(
                "DDA_AUTO", new BigDecimal("250000.01"), LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
    }

    @Test
    @DisplayName("produto sem configuração de limite lança BusinessException")
    void produtoDesconhecido() {
        assertThrows(BusinessException.class, () -> regra.validar(TestFixtures.criarContext(
                "CARTAO", new BigDecimal("10"), LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
    }
}
