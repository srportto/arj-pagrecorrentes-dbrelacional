package br.com.srportto.contratocommand.application.defaultservice.contratacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.defaultservice.contratacao.rules.DataFimVigenciaInvalida;
import br.com.srportto.contratocommand.application.defaultservice.contratacao.rules.MetadadoRule;
import br.com.srportto.contratocommand.application.defaultservice.contratacao.rules.ValorLimiteContrato;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do ContratacaoValidator")
class ContratacaoValidatorTest {

    private final ContratacaoValidator validator = new ContratacaoValidator(
            List.of(new DataFimVigenciaInvalida(), new ValorLimiteContrato(), new MetadadoRule()));

    @Test
    @DisplayName("expõe getLogCode e getRules")
    void exposeMetadados() {
        assertNotNull(validator.getLogCode());
        assertEquals(3, validator.getRules().size());
    }

    @Test
    @DisplayName("validar percorre as regras e passa para requisição válida")
    void validarRequisicaoValida() {
        assertDoesNotThrow(() -> validator.validar(TestFixtures.criarRequestPix()));
    }

    @Test
    @DisplayName("validar propaga BusinessException de uma regra violada")
    void validarRequisicaoInvalida() {
        assertThrows(BusinessException.class, () -> validator.validar(TestFixtures.criarRequest(
                "PIX_AUTO", new BigDecimal("100"), LocalDate.now().minusDays(1), null)));
    }
}
