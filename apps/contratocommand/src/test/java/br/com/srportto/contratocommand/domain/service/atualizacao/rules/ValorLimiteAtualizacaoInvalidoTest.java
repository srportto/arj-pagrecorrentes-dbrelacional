package br.com.srportto.contratocommand.domain.service.atualizacao.rules;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.port.in.AtualizarDadosRecorrenciaCommand;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes da regra ValorLimiteAtualizacaoInvalido")
class ValorLimiteAtualizacaoInvalidoTest {

    private final ValorLimiteAtualizacaoInvalido regra = new ValorLimiteAtualizacaoInvalido();

    @Test
    @DisplayName("aceita sempre retorna true")
    void aceitaTrue() {
        assertTrue(regra.aceita(TestFixtures.atualizarContext("id", TipoProduto.PIX_AUTO)));
    }

    @Test
    @DisplayName("validar lança BusinessException quando valorLimite é zero")
    void valorZeroLanca() {
        AtualizarDadosRecorrenciaCommand context = comValorLimite(BigDecimal.ZERO);
        assertThrows(BusinessException.class, () -> regra.validar(context));
    }

    @Test
    @DisplayName("validar lança BusinessException quando valorLimite é negativo")
    void valorNegativoLanca() {
        AtualizarDadosRecorrenciaCommand context = comValorLimite(new BigDecimal("-10.00"));
        assertThrows(BusinessException.class, () -> regra.validar(context));
    }

    @Test
    @DisplayName("validar aceita valorLimite positivo e campo ausente (não altera)")
    void valorPositivoOuAusenteOk() {
        assertDoesNotThrow(() -> regra.validar(comValorLimite(new BigDecimal("100.00"))));
        assertDoesNotThrow(() -> regra.validar(comValorLimite(null)));
    }

    private AtualizarDadosRecorrenciaCommand comValorLimite(BigDecimal valorLimite) {
        return new AtualizarDadosRecorrenciaCommand(
                "id", TipoProduto.PIX_AUTO, TipoProduto.PIX_AUTO, StatusAutorizacao.ATIVA,
                valorLimite, null, null, null, "C1", null);
    }
}
