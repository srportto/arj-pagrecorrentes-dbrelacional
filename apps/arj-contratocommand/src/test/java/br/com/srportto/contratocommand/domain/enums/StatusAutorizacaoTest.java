package br.com.srportto.contratocommand.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do enum StatusAutorizacao")
class StatusAutorizacaoTest {

    @Test
    @DisplayName("obterStatusEnumPorIdStatus retorna o enum correspondente para cada código")
    void obtemEnumPorIdParaTodosOsCodigos() {
        for (StatusAutorizacao status : StatusAutorizacao.values()) {
            StatusAutorizacao encontrado =
                    StatusAutorizacao.obterStatusEnumPorIdStatus(status.getStatusAutorizacao());
            assertEquals(status, encontrado);
        }
    }

    @Test
    @DisplayName("obterStatusEnumPorIdStatus lança IllegalArgumentException para código desconhecido")
    void lancaParaCodigoDesconhecido() {
        assertThrows(IllegalArgumentException.class,
                () -> StatusAutorizacao.obterStatusEnumPorIdStatus(999L));
    }

    @Test
    @DisplayName("getStatusAutorizacao expõe o código numérico")
    void exposeCodigo() {
        assertEquals(4L, StatusAutorizacao.ATIVA.getStatusAutorizacao());
        assertEquals(1L, StatusAutorizacao.RECEBIDA.getStatusAutorizacao());
    }

    @Test
    @DisplayName("RECEBIDA pode transicionar para PENDENTE_ACEITE, EM_PROCESSO_ATIVACAO e REJEITADA")
    void transicoesDeRecebida() {
        assertTrue(StatusAutorizacao.RECEBIDA.podeTransicionarPara(StatusAutorizacao.PENDENTE_ACEITE));
        assertTrue(StatusAutorizacao.RECEBIDA.podeTransicionarPara(StatusAutorizacao.EM_PROCESSO_ATIVACAO));
        assertTrue(StatusAutorizacao.RECEBIDA.podeTransicionarPara(StatusAutorizacao.REJEITADA));
        assertFalse(StatusAutorizacao.RECEBIDA.podeTransicionarPara(StatusAutorizacao.ATIVA));
        assertFalse(StatusAutorizacao.RECEBIDA.podeTransicionarPara(StatusAutorizacao.EXPIRADA));
    }

    @Test
    @DisplayName("PENDENTE_ACEITE pode transicionar para EM_PROCESSO_ATIVACAO, REJEITADA e EXPIRADA")
    void transicoesDePendenteAceite() {
        assertTrue(StatusAutorizacao.PENDENTE_ACEITE.podeTransicionarPara(StatusAutorizacao.EM_PROCESSO_ATIVACAO));
        assertTrue(StatusAutorizacao.PENDENTE_ACEITE.podeTransicionarPara(StatusAutorizacao.REJEITADA));
        assertTrue(StatusAutorizacao.PENDENTE_ACEITE.podeTransicionarPara(StatusAutorizacao.EXPIRADA));
        assertFalse(StatusAutorizacao.PENDENTE_ACEITE.podeTransicionarPara(StatusAutorizacao.ATIVA));
    }

    @Test
    @DisplayName("EM_PROCESSO_ATIVACAO pode transicionar para ATIVA, REJEITADA e EXPIRADA")
    void transicoesDeEmProcessoAtivacao() {
        assertTrue(StatusAutorizacao.EM_PROCESSO_ATIVACAO.podeTransicionarPara(StatusAutorizacao.ATIVA));
        assertTrue(StatusAutorizacao.EM_PROCESSO_ATIVACAO.podeTransicionarPara(StatusAutorizacao.REJEITADA));
        assertTrue(StatusAutorizacao.EM_PROCESSO_ATIVACAO.podeTransicionarPara(StatusAutorizacao.EXPIRADA));
        assertFalse(StatusAutorizacao.EM_PROCESSO_ATIVACAO.podeTransicionarPara(StatusAutorizacao.CANCELADA));
    }

    @Test
    @DisplayName("ATIVA pode transicionar para CANCELADA, FINALIZADA e REJEITADA, mas não EXPIRADA")
    void transicoesDeAtiva() {
        assertTrue(StatusAutorizacao.ATIVA.podeTransicionarPara(StatusAutorizacao.CANCELADA));
        assertTrue(StatusAutorizacao.ATIVA.podeTransicionarPara(StatusAutorizacao.FINALIZADA));
        assertTrue(StatusAutorizacao.ATIVA.podeTransicionarPara(StatusAutorizacao.REJEITADA));
        assertFalse(StatusAutorizacao.ATIVA.podeTransicionarPara(StatusAutorizacao.EXPIRADA));
    }

    @Test
    @DisplayName("caminhos usados pela decisão (aprovar/rejeitar/expirar) já existem no grafo, sem alteração")
    void caminhosDaDecisaoJaExistemNoGrafo() {
        // RECEBIDA -> REJEITADA: usado por REJEITAR e EXPIRAR (capacidade decisao-autorizacao)
        assertTrue(StatusAutorizacao.RECEBIDA.podeTransicionarPara(StatusAutorizacao.REJEITADA));
        // RECEBIDA -> EM_PROCESSO_ATIVACAO -> ATIVA: os dois saltos usados por APROVAR, na mesma transação
        assertTrue(StatusAutorizacao.RECEBIDA.podeTransicionarPara(StatusAutorizacao.EM_PROCESSO_ATIVACAO));
        assertTrue(StatusAutorizacao.EM_PROCESSO_ATIVACAO.podeTransicionarPara(StatusAutorizacao.ATIVA));
    }

    @Test
    @DisplayName("estados terminais não transicionam para nenhum outro estado")
    void estadosTerminaisNaoTransicionam() {
        for (StatusAutorizacao terminal : new StatusAutorizacao[] {
                StatusAutorizacao.CANCELADA, StatusAutorizacao.REJEITADA,
                StatusAutorizacao.EXPIRADA, StatusAutorizacao.FINALIZADA}) {
            for (StatusAutorizacao destino : StatusAutorizacao.values()) {
                assertFalse(terminal.podeTransicionarPara(destino),
                        terminal + " não deveria transicionar para " + destino);
            }
        }
    }
}
