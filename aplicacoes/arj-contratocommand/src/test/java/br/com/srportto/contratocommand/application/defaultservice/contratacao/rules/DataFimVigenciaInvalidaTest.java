package br.com.srportto.contratocommand.application.defaultservice.contratacao.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes da regra DataFimVigenciaInvalida")
class DataFimVigenciaInvalidaTest {

    private final DataFimVigenciaInvalida regra = new DataFimVigenciaInvalida();

    @Test
    @DisplayName("aceita sempre retorna true")
    void aceitaTrue() {
        assertTrue(regra.aceita(TestFixtures.criarRequestPix()));
    }

    @Test
    @DisplayName("validar lança BusinessException quando a data está no passado")
    void dataPassadoLanca() {
        CriarAutorizacaoRequest request = TestFixtures.criarRequest(
                "PIX_AUTO", new BigDecimal("100"), LocalDate.now().minusDays(1), null);
        assertThrows(BusinessException.class, () -> regra.validar(request));
    }

    @Test
    @DisplayName("validar aceita data futura e data nula")
    void dataFuturaOuNulaOk() {
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarRequest(
                "PIX_AUTO", new BigDecimal("100"), LocalDate.now().plusDays(10), null)));
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarRequest(
                "PIX_AUTO", new BigDecimal("100"), null, null)));
    }
}
