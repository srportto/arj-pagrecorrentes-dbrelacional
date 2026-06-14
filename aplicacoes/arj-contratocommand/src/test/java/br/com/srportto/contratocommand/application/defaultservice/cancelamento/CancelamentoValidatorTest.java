package br.com.srportto.contratocommand.application.defaultservice.cancelamento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.defaultservice.cancelamento.rules.TipoProdutoCancelamento;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do CancelamentoValidator")
class CancelamentoValidatorTest {

    private final CancelamentoValidator validator =
            new CancelamentoValidator(List.of(new TipoProdutoCancelamento()));

    @Test
    @DisplayName("expõe getLogCode e getRules")
    void exposeMetadados() {
        assertNotNull(validator.getLogCode());
        assertEquals(1, validator.getRules().size());
    }

    @Test
    @DisplayName("validar passa quando produto do header e da autorização coincidem")
    void validarOk() {
        CancelarAutorizacaoRequestDto request = TestFixtures.cancelarRequest("id", TipoProduto.PIX_AUTO);
        request.setTipoProdutoDoIdAutorizacao(TipoProduto.PIX_AUTO);
        assertDoesNotThrow(() -> validator.validar(request));
    }

    @Test
    @DisplayName("validar propaga BusinessException quando produtos divergem")
    void validarDivergente() {
        CancelarAutorizacaoRequestDto request = TestFixtures.cancelarRequest("id", TipoProduto.PIX_AUTO);
        request.setTipoProdutoDoIdAutorizacao(TipoProduto.DDA_AUTO);
        assertThrows(BusinessException.class, () -> validator.validar(request));
    }
}
