package br.com.srportto.contratocommand.domain.service.contratacao;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.service.contratacao.rules.DataFimVigenciaInvalida;
import br.com.srportto.contratocommand.domain.service.contratacao.rules.MetadadoRule;
import br.com.srportto.contratocommand.domain.service.contratacao.rules.ProdutoSuportado;
import br.com.srportto.contratocommand.domain.service.contratacao.rules.ValorLimiteContrato;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
        assertDoesNotThrow(() -> validator.validar(TestFixtures.criarContextPix()));
    }

    @Test
    @DisplayName("validar propaga BusinessException de uma regra violada")
    void validarRequisicaoInvalida() {
        assertThrows(BusinessException.class, () -> validator.validar(TestFixtures.criarContext(
                TipoProduto.PIX_AUTO, new BigDecimal("100"), LocalDate.now().minusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
    }

    @Test
    @DisplayName("ProdutoSuportado (primeira na lista) reporta produto não suportado antes de outra regra violada")
    void produtoSuportadoExecutaAntesDasDemaisRegras() {
        ContratacaoValidator validatorComProdutoSuportadoPrimeiro = new ContratacaoValidator(
                List.of(new ProdutoSuportado(), new DataFimVigenciaInvalida(), new ValorLimiteContrato(), new MetadadoRule()));

        // Ambas as regras violariam (produto nulo e data no passado); ProdutoSuportado (primeira) deve reportar antes.
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validatorComProdutoSuportadoPrimeiro.validar(TestFixtures.criarContext(
                        null, new BigDecimal("100"), LocalDate.now().minusDays(1), null,
                        TipoJornadaAutorizacao.SPI_J1)));

        assertEquals("Produto nao suportado ou invalido (tipoProduto: null)", ex.getMessage());
    }
}
