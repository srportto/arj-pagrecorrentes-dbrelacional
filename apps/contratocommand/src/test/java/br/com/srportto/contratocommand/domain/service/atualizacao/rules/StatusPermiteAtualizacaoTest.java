package br.com.srportto.contratocommand.domain.service.atualizacao.rules;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.port.in.AtualizarDadosRecorrenciaCommand;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Só ATIVA permite atualização de dados; demais status devem falhar (ver design.md, D2). */
@DisplayName("Testes da regra StatusPermiteAtualizacao")
class StatusPermiteAtualizacaoTest {

    private final StatusPermiteAtualizacao regra = new StatusPermiteAtualizacao();

    @Test
    @DisplayName("aceita sempre retorna true")
    void aceitaTrue() {
        AtualizarDadosRecorrenciaCommand context = TestFixtures.atualizarContext("11111111-1111-1111-1111-111111111111", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.ATIVA);
        assertTrue(regra.aceita(context));
    }

    @Test
    @DisplayName("autorização em ATIVA: permitida, não lança")
    void autEmAtiva_Permitida() {
        AtualizarDadosRecorrenciaCommand context = TestFixtures.atualizarContext("11111111-1111-1111-1111-111111111111", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.ATIVA);
        assertDoesNotThrow(() -> regra.validar(context));
    }

    @Test
    @DisplayName("autorização em RECEBIDA: não permitida, lança BusinessException")
    void autEmRecebida_NaoPermitida() {
        AtualizarDadosRecorrenciaCommand context = TestFixtures.atualizarContext("11111111-1111-1111-1111-111111111111", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.RECEBIDA);
        BusinessException ex = assertThrows(BusinessException.class, () -> regra.validar(context));
        assertTrue(ex.getMessage().contains("não permite atualização de dados"));
    }

    @Test
    @DisplayName("autorização em PENDENTE_ACEITE: não permitida, lança BusinessException")
    void autEmPendenteAceite_NaoPermitida() {
        AtualizarDadosRecorrenciaCommand context = TestFixtures.atualizarContext("11111111-1111-1111-1111-111111111111", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.PENDENTE_ACEITE);
        assertThrows(BusinessException.class, () -> regra.validar(context));
    }

    @Test
    @DisplayName("autorização em EM_PROCESSO_ATIVACAO: não permitida, lança BusinessException")
    void autEmProcessoAtivacao_NaoPermitida() {
        AtualizarDadosRecorrenciaCommand context = TestFixtures.atualizarContext("11111111-1111-1111-1111-111111111111", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.EM_PROCESSO_ATIVACAO);
        assertThrows(BusinessException.class, () -> regra.validar(context));
    }

    @Test
    @DisplayName("autorização em CANCELADA: não permitida, lança BusinessException")
    void autEmCancelada_NaoPermitida() {
        AtualizarDadosRecorrenciaCommand context = TestFixtures.atualizarContext("11111111-1111-1111-1111-111111111111", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.CANCELADA);
        assertThrows(BusinessException.class, () -> regra.validar(context));
    }

    @Test
    @DisplayName("autorização em REJEITADA: não permitida, lança BusinessException")
    void autEmRejeitada_NaoPermitida() {
        AtualizarDadosRecorrenciaCommand context = TestFixtures.atualizarContext("11111111-1111-1111-1111-111111111111", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.REJEITADA);
        assertThrows(BusinessException.class, () -> regra.validar(context));
    }

    @Test
    @DisplayName("autorização em EXPIRADA: não permitida, lança BusinessException")
    void autEmExpirada_NaoPermitida() {
        AtualizarDadosRecorrenciaCommand context = TestFixtures.atualizarContext("11111111-1111-1111-1111-111111111111", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.EXPIRADA);
        assertThrows(BusinessException.class, () -> regra.validar(context));
    }

    @Test
    @DisplayName("autorização em FINALIZADA: não permitida, lança BusinessException")
    void autEmFinalizada_NaoPermitida() {
        AtualizarDadosRecorrenciaCommand context = TestFixtures.atualizarContext("11111111-1111-1111-1111-111111111111", TipoProduto.PIX_AUTO)
                .comAutorizacaoCarregada(TipoProduto.PIX_AUTO, StatusAutorizacao.FINALIZADA);
        assertThrows(BusinessException.class, () -> regra.validar(context));
    }
}
