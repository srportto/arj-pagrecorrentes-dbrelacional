package br.com.srportto.contratocommand.domain.service.atualizacao.rules;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.port.in.AtualizarDadosRecorrenciaCommand;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes da regra TipoProdutoAtualizacao")
class TipoProdutoAtualizacaoTest {

    private final TipoProdutoAtualizacao regra = new TipoProdutoAtualizacao();

    @Test
    @DisplayName("aceita sempre retorna true")
    void aceitaTrue() {
        assertTrue(regra.aceita(TestFixtures.atualizarContext("id", TipoProduto.PIX_AUTO)));
    }

    @Test
    @DisplayName("produto do header igual ao da autorização não lança")
    void produtosIguais() {
        AtualizarDadosRecorrenciaCommand context = TestFixtures.atualizarContext("id", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.ATIVA);
        assertDoesNotThrow(() -> regra.validar(context));
    }

    @Test
    @DisplayName("produto do header divergente do da autorização lança BusinessException")
    void produtosDivergentes() {
        AtualizarDadosRecorrenciaCommand context = TestFixtures.atualizarContext("id", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.DDA_AUTO, StatusAutorizacao.ATIVA);
        assertThrows(BusinessException.class, () -> regra.validar(context));
    }
}
