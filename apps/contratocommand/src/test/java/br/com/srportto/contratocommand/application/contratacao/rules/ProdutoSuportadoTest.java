package br.com.srportto.contratocommand.application.contratacao.rules;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.contratacao.ContratacaoContext;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("Testes da regra ProdutoSuportado")
class ProdutoSuportadoTest {

    private final ProdutoSuportado regra = new ProdutoSuportado();

    @Test
    @DisplayName("aceita sempre retorna true")
    void aceitaTrue() {
        assertTrue(regra.aceita(TestFixtures.criarContextPix()));
    }

    @Test
    @DisplayName("validar aceita produto suportado em qualquer caixa")
    void produtoSuportadoEmQualquerCaixa() {
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarContext(
                "pix_auto", BigDecimal.ONE, LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarContext(
                "PIX_AUTO", BigDecimal.ONE, LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarContext(
                "DdA_aUtO", BigDecimal.ONE, LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
    }

    @Test
    @DisplayName("validar lança BusinessException para produto desconhecido")
    void produtoDesconhecidoLanca() {
        ContratacaoContext context = TestFixtures.criarContext(
                "CARTAO_CREDITO", BigDecimal.ONE, LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1);

        BusinessException ex = assertThrows(BusinessException.class, () -> regra.validar(context));
        assertEquals("Produto nao suportado ou invalido (tipoProduto: CARTAO_CREDITO)", ex.getMessage());
    }

    @Test
    @DisplayName("validar lança BusinessException para produto nulo")
    void produtoNuloLanca() {
        ContratacaoContext context = TestFixtures.criarContext(
                null, BigDecimal.ONE, LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1);

        assertThrows(BusinessException.class, () -> regra.validar(context));
    }

    @Test
    @DisplayName("validar lança BusinessException para produto conhecido porém desabilitado para contratar")
    void produtoDesabilitadoParaContratarLanca() {
        try (var tipoProdutoMock = mockStatic(TipoProduto.class, CALLS_REAL_METHODS)) {
            TipoProduto pixDesabilitado = mock(TipoProduto.class);
            when(pixDesabilitado.name()).thenReturn("PIX_AUTO");
            when(pixDesabilitado.habilitadoParaContratar()).thenReturn(false);
            tipoProdutoMock.when(TipoProduto::values).thenReturn(new TipoProduto[] { pixDesabilitado });

            ContratacaoContext context = TestFixtures.criarContext(
                    "PIX_AUTO", BigDecimal.ONE, LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1);

            BusinessException ex = assertThrows(BusinessException.class, () -> regra.validar(context));
            assertEquals("Produto nao suportado ou invalido (tipoProduto: PIX_AUTO)", ex.getMessage());
        }
    }
}
