package br.com.srportto.contratocommand.application.cancelamento;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.cancelamento.rules.ProdutoSuportadoCancelamento;
import br.com.srportto.contratocommand.application.cancelamento.rules.TipoProdutoCancelamento;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do CancelamentoValidator")
class CancelamentoValidatorTest {

    private final CancelamentoValidator validator =
            new CancelamentoValidator(List.of(new TipoProdutoCancelamento()));

    private final CancelamentoValidator validatorComOrdemDeProducao = new CancelamentoValidator(
            List.of(new ProdutoSuportadoCancelamento(), new TipoProdutoCancelamento()));

    @Test
    @DisplayName("expõe getLogCode e getRules")
    void exposeMetadados() {
        assertNotNull(validator.getLogCode());
        assertEquals(1, validator.getRules().size());
    }

    @Test
    @DisplayName("validar passa quando produto do header e da autorização coincidem")
    void validarOk() {
        CancelamentoContext context = TestFixtures.cancelarContext("id", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.ATIVA);
        assertDoesNotThrow(() -> validator.validar(context));
    }

    @Test
    @DisplayName("validar propaga BusinessException quando produtos divergem")
    void validarDivergente() {
        CancelamentoContext context = TestFixtures.cancelarContext("id", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.DDA_AUTO, StatusAutorizacao.ATIVA);
        assertThrows(BusinessException.class, () -> validator.validar(context));
    }

    @Test
    @DisplayName("ProdutoSuportadoCancelamento executa antes de TipoProdutoCancelamento")
    void produtoSuportadoCancelamentoExecutaPrimeiro() {
        TipoProduto produtoDesabilitado = mock(TipoProduto.class);
        when(produtoDesabilitado.habilitadoParaCancelar()).thenReturn(false);

        CancelamentoContext context = TestFixtures.cancelarContext("id", produtoDesabilitado)
                .comAutorizacaoCarregada(TipoProduto.DDA_AUTO, StatusAutorizacao.ATIVA);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> validatorComOrdemDeProducao.validar(context));
        assertTrue(ex.getMessage().contains("nao habilitado para cancelamento"));
    }
}
