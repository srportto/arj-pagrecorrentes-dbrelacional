package br.com.srportto.contratocommand.domain.service.cancelamento.rules;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.cancelamento.CancelamentoContext;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Só ATIVA tem aresta para CANCELADA no grafo de StatusAutorizacao; demais status devem falhar. */
@DisplayName("Testes da regra TransicaoStatusValida")
class TransicaoStatusValidaTest {

    private final TransicaoStatusValida regra = new TransicaoStatusValida();

    @Test
    @DisplayName("aceita sempre retorna true")
    void aceitaTrue() {
        CancelamentoContext context = TestFixtures.cancelarContext("id", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.ATIVA);
        assertTrue(regra.aceita(context));
    }

    @Test
    @DisplayName("autorização em ATIVA: transição permitida, não lança")
    void autEmAtiva_Permitida() {
        CancelamentoContext context = TestFixtures.cancelarContext("id", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.ATIVA);
        assertDoesNotThrow(() -> regra.validar(context));
    }

    @Test
    @DisplayName("autorização em PENDENTE_ACEITE: transição não permitida (grafo não permite), lança BusinessException")
    void autEmPendenteAceite_NaoPermitida() {
        CancelamentoContext context = TestFixtures.cancelarContext("id", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.PENDENTE_ACEITE);
        BusinessException ex = assertThrows(BusinessException.class, () -> regra.validar(context));
        assertTrue(ex.getMessage().contains("não permite cancelamento"));
    }

    @Test
    @DisplayName("autorização em CANCELADA: transição não permitida, lança BusinessException")
    void autEmCancelada_NaoPermitida() {
        CancelamentoContext context = TestFixtures.cancelarContext("id", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.CANCELADA);
        BusinessException ex = assertThrows(BusinessException.class, () -> regra.validar(context));
        assertTrue(ex.getMessage().contains("não permite cancelamento"));
    }

    @Test
    @DisplayName("autorização em REJEITADA: transição não permitida, lança BusinessException")
    void autEmRejeitada_NaoPermitida() {
        CancelamentoContext context = TestFixtures.cancelarContext("id", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.REJEITADA);
        BusinessException ex = assertThrows(BusinessException.class, () -> regra.validar(context));
        assertTrue(ex.getMessage().contains("não permite cancelamento"));
    }

    @Test
    @DisplayName("autorização em EXPIRADA: transição não permitida, lança BusinessException")
    void autEmExpirada_NaoPermitida() {
        CancelamentoContext context = TestFixtures.cancelarContext("id", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.EXPIRADA);
        BusinessException ex = assertThrows(BusinessException.class, () -> regra.validar(context));
        assertTrue(ex.getMessage().contains("não permite cancelamento"));
    }

    @Test
    @DisplayName("autorização em FINALIZADA: transição não permitida, lança BusinessException")
    void autEmFinalizada_NaoPermitida() {
        CancelamentoContext context = TestFixtures.cancelarContext("id", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.FINALIZADA);
        BusinessException ex = assertThrows(BusinessException.class, () -> regra.validar(context));
        assertTrue(ex.getMessage().contains("não permite cancelamento"));
    }

    @Test
    @DisplayName("autorização em RECEBIDA: transição não permitida (esperado apenas ATIVA), lança BusinessException")
    void autEmRecebida_NaoPermitida() {
        CancelamentoContext context = TestFixtures.cancelarContext("id", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.RECEBIDA);
        BusinessException ex = assertThrows(BusinessException.class, () -> regra.validar(context));
        assertTrue(ex.getMessage().contains("não permite cancelamento"));
    }

    @Test
    @DisplayName("autorização em EM_PROCESSO_ATIVACAO: transição não permitida, lança BusinessException")
    void autEmProcessoAtivacao_NaoPermitida() {
        CancelamentoContext context = TestFixtures.cancelarContext("id", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.EM_PROCESSO_ATIVACAO);
        BusinessException ex = assertThrows(BusinessException.class, () -> regra.validar(context));
        assertTrue(ex.getMessage().contains("não permite cancelamento"));
    }
}
