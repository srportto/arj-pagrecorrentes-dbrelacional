package br.com.srportto.eventosconsumer.application.eventos;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("Testes do ProcessarEventoAutorizacaoUseCase")
class ProcessarEventoAutorizacaoUseCaseTest {

    private final ProcessarEventoAutorizacaoUseCase useCase = new ProcessarEventoAutorizacaoUseCase();

    @Test
    @DisplayName("processa (loga) um evento válido sem lançar exceção")
    void processaEventoValido() {
        EventoAutorizacao evento = EventoAutorizacao.newBuilder()
                .setIdAutorizacao(UUID.randomUUID())
                .setIdParticaoConta(950)
                .setDataFimVigencia(LocalDate.now())
                .setTipoProduto(1L)
                .setStatus(4)
                .setDataHoraInclusao(LocalDateTime.now())
                .setDataHoraUltimaAtlz(LocalDateTime.now())
                .setCodigoCanalContratacao("canal")
                .build();

        assertDoesNotThrow(() -> useCase.processar(evento, "CRIACAO"));
    }

}
