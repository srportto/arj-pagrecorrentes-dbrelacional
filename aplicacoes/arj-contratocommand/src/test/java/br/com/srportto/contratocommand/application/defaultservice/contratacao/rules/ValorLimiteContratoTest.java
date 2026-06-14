package br.com.srportto.contratocommand.application.defaultservice.contratacao.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes da regra ValorLimiteContrato")
class ValorLimiteContratoTest {

    private final ValorLimiteContrato regra = new ValorLimiteContrato();

    @Test
    @DisplayName("PIX_AUTO dentro do limite passa, acima de 1.000.000 lança")
    void pixAuto() {
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarRequest(
                "PIX_AUTO", new BigDecimal("1000000"), LocalDate.now().plusDays(1), null)));
        assertThrows(BusinessException.class, () -> regra.validar(TestFixtures.criarRequest(
                "PIX_AUTO", new BigDecimal("1000000.01"), LocalDate.now().plusDays(1), null)));
    }

    @Test
    @DisplayName("DDA_AUTO dentro do limite passa, acima de 250.000 lança")
    void ddaAuto() {
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarRequest(
                "DDA_AUTO", new BigDecimal("250000"), LocalDate.now().plusDays(1), null)));
        assertThrows(BusinessException.class, () -> regra.validar(TestFixtures.criarRequest(
                "DDA_AUTO", new BigDecimal("250000.01"), LocalDate.now().plusDays(1), null)));
    }

    @Test
    @DisplayName("produto sem configuração de limite lança BusinessException")
    void produtoDesconhecido() {
        assertThrows(BusinessException.class, () -> regra.validar(TestFixtures.criarRequest(
                "CARTAO", new BigDecimal("10"), LocalDate.now().plusDays(1), null)));
    }
}
