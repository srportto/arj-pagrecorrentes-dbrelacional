package br.com.srportto.contratocommand.domain.service.cancelamento.rules;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.port.in.CancelarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes da regra ProdutoSuportadoCancelamento")
class ProdutoSuportadoCancelamentoTest {

    private final ProdutoSuportadoCancelamento regra = new ProdutoSuportadoCancelamento();

    @Test
    @DisplayName("aceita sempre retorna true")
    void aceitaTrue() {
        assertTrue(regra.aceita(TestFixtures.cancelarContext("11111111-1111-1111-1111-111111111111", TipoProduto.PIX_AUTO)));
    }

    @Test
    @DisplayName("produto habilitado para cancelar não lança")
    void produtoHabilitadoNaoLanca() {
        CancelarAutorizacaoCommand context = TestFixtures.cancelarContext("11111111-1111-1111-1111-111111111111", TipoProduto.PIX_AUTO);
        assertDoesNotThrow(() -> regra.validar(context));
    }

    @Test
    @DisplayName("produto não habilitado para cancelar lança BusinessException")
    void produtoDesabilitadoLanca() {
        TipoProduto produtoDesabilitado = mock(TipoProduto.class);
        when(produtoDesabilitado.habilitadoParaCancelar()).thenReturn(false);

        CancelarAutorizacaoCommand context = TestFixtures.cancelarContext("11111111-1111-1111-1111-111111111111", produtoDesabilitado);

        assertThrows(BusinessException.class, () -> regra.validar(context));
    }

}
