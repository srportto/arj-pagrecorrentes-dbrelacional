package br.com.srportto.contratocommand.domain.service.contratacao.rules;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.contratacao.ContratacaoContext;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes da regra DataFimVigenciaInvalida")
class DataFimVigenciaInvalidaTest {

    private final DataFimVigenciaInvalida regra = new DataFimVigenciaInvalida();

    @Test
    @DisplayName("aceita sempre retorna true")
    void aceitaTrue() {
        assertTrue(regra.aceita(TestFixtures.criarContextPix()));
    }

    @Test
    @DisplayName("validar lança BusinessException quando a data está no passado")
    void dataPassadoLanca() {
        ContratacaoContext context = TestFixtures.criarContext(
                "PIX_AUTO", new BigDecimal("100"), LocalDate.now().minusDays(1), null, TipoJornadaAutorizacao.SPI_J1);
        assertThrows(BusinessException.class, () -> regra.validar(context));
    }

    @Test
    @DisplayName("validar aceita data futura e data nula")
    void dataFuturaOuNulaOk() {
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarContext(
                "PIX_AUTO", new BigDecimal("100"), LocalDate.now().plusDays(10), null, TipoJornadaAutorizacao.SPI_J1)));
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarContext(
                "PIX_AUTO", new BigDecimal("100"), null, null, TipoJornadaAutorizacao.SPI_J1)));
    }
}
