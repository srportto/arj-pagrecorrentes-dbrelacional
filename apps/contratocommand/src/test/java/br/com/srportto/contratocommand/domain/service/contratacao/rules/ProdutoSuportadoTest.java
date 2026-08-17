package br.com.srportto.contratocommand.domain.service.contratacao.rules;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A resolução de nome de produto desconhecido (string→enum) acontece no controller — ver
 * {@code TipoProdutoTest#lancaPorNomeDesconhecido} e
 * {@code AutorizacaoControllerTest#insertComTipoProdutoDesconhecidoLancaAntesDoUseCase}. Esta
 * classe testa só a regra em si, que já recebe um {@link TipoProduto} resolvido (ou nulo).
 */
@DisplayName("Testes da regra ProdutoSuportado")
class ProdutoSuportadoTest {

    private final ProdutoSuportado regra = new ProdutoSuportado();

    @Test
    @DisplayName("aceita sempre retorna true")
    void aceitaTrue() {
        assertTrue(regra.aceita(TestFixtures.criarContextPix()));
    }

    @Test
    @DisplayName("validar aceita produto habilitado para contratar")
    void produtoHabilitadoNaoLanca() {
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarContextPix()));
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarContextDda()));
    }

    @Test
    @DisplayName("validar lança BusinessException para produto nulo")
    void produtoNuloLanca() {
        CriarAutorizacaoCommand context = TestFixtures.criarContext(
                null, BigDecimal.ONE, LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1);

        assertThrows(BusinessException.class, () -> regra.validar(context));
    }

    @Test
    @DisplayName("validar lança BusinessException para produto conhecido porém desabilitado para contratar")
    void produtoDesabilitadoParaContratarLanca() {
        TipoProduto pixDesabilitado = mock(TipoProduto.class);
        when(pixDesabilitado.habilitadoParaContratar()).thenReturn(false);
        when(pixDesabilitado.toString()).thenReturn("PIX_AUTO");

        CriarAutorizacaoCommand context = TestFixtures.criarContext(
                pixDesabilitado, BigDecimal.ONE, LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1);

        BusinessException ex = assertThrows(BusinessException.class, () -> regra.validar(context));
        assertEquals("Produto nao suportado ou invalido (tipoProduto: PIX_AUTO)", ex.getMessage());
    }
}
