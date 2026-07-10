package br.com.srportto.contratocommand.application.cancelamento.rules;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.cancelamento.CancelamentoContext;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes da regra TipoProdutoCancelamento")
class TipoProdutoCancelamentoTest {

    private final TipoProdutoCancelamento regra = new TipoProdutoCancelamento();

    @Test
    @DisplayName("aceita sempre retorna true")
    void aceitaTrue() {
        assertTrue(regra.aceita(TestFixtures.cancelarContext("id", TipoProduto.PIX_AUTO)));
    }

    @Test
    @DisplayName("produto do header igual ao da autorização não lança")
    void produtosIguais() {
        CancelamentoContext context = TestFixtures.cancelarContext("id", TipoProduto.PIX_AUTO)
                .comProdutoAutorizacao(TipoProduto.PIX_AUTO);
        assertDoesNotThrow(() -> regra.validar(context));
    }

    @Test
    @DisplayName("produto do header divergente do da autorização lança BusinessException")
    void produtosDivergentes() {
        CancelamentoContext context = TestFixtures.cancelarContext("id", TipoProduto.PIX_AUTO)
                .comProdutoAutorizacao(TipoProduto.DDA_AUTO);
        assertThrows(BusinessException.class, () -> regra.validar(context));
    }
}
