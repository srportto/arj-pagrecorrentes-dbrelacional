package br.com.srportto.eventosconsumer.domain.enums;

import br.com.srportto.eventosconsumer.domain.exception.EventoAutorizacaoInvalidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Testes do enum TipoEventoAutorizacao")
class TipoEventoAutorizacaoTest {

    @Test
    @DisplayName("porStatus deriva o tipo correto para cada um dos 8 status")
    void derivaTipoParaCadaStatus() {
        assertEquals(TipoEventoAutorizacao.ATIVACAO,
                TipoEventoAutorizacao.porStatus(StatusAutorizacao.ATIVA.getStatusAutorizacao()));
        assertEquals(TipoEventoAutorizacao.CANCELAMENTO,
                TipoEventoAutorizacao.porStatus(StatusAutorizacao.CANCELADA.getStatusAutorizacao()));
        assertEquals(TipoEventoAutorizacao.REJEICAO,
                TipoEventoAutorizacao.porStatus(StatusAutorizacao.REJEITADA.getStatusAutorizacao()));
        assertEquals(TipoEventoAutorizacao.EXPIRACAO,
                TipoEventoAutorizacao.porStatus(StatusAutorizacao.EXPIRADA.getStatusAutorizacao()));
    }

    @Test
    @DisplayName("porStatus lança exceção para status desconhecido")
    void lancaParaStatusDesconhecido() {
        assertThrows(EventoAutorizacaoInvalidoException.class, () -> TipoEventoAutorizacao.porStatus(99L));
    }

    @Test
    @DisplayName("todos os 8 valores do enum são atingíveis via porStatus (bijeção completa)")
    void bijecaoCompleta() {
        for (StatusAutorizacao status : StatusAutorizacao.values()) {
            TipoEventoAutorizacao.porStatus(status.getStatusAutorizacao());
        }
        assertEquals(8, TipoEventoAutorizacao.values().length);
    }
}
