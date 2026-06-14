package br.com.srportto.contratocommand.application.defaultservice.cancelamento.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes da regra TipoProdutoCancelamento")
class TipoProdutoCancelamentoTest {

    private final TipoProdutoCancelamento regra = new TipoProdutoCancelamento();

    @Test
    @DisplayName("aceita sempre retorna true")
    void aceitaTrue() {
        assertTrue(regra.aceita(TestFixtures.cancelarRequest("id", TipoProduto.PIX_AUTO)));
    }

    @Test
    @DisplayName("produto do header igual ao da autorização não lança")
    void produtosIguais() {
        CancelarAutorizacaoRequestDto request = TestFixtures.cancelarRequest("id", TipoProduto.PIX_AUTO);
        request.setTipoProdutoDoIdAutorizacao(TipoProduto.PIX_AUTO);
        assertDoesNotThrow(() -> regra.validar(request));
    }

    @Test
    @DisplayName("produto do header divergente do da autorização lança BusinessException")
    void produtosDivergentes() {
        CancelarAutorizacaoRequestDto request = TestFixtures.cancelarRequest("id", TipoProduto.PIX_AUTO);
        request.setTipoProdutoDoIdAutorizacao(TipoProduto.DDA_AUTO);
        assertThrows(BusinessException.class, () -> regra.validar(request));
    }
}
