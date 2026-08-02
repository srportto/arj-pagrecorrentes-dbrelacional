package br.com.srportto.eventosconsumer.entrypoint.kafka;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import br.com.srportto.eventosconsumer.application.eventos.ProcessarEventoAutorizacaoUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do EventoAutorizacaoKafkaListener")
class EventoAutorizacaoKafkaListenerTest {

    @Mock
    private ProcessarEventoAutorizacaoUseCase useCase;

    private EventoAutorizacaoKafkaListener listener;

    private void inicializar() {
        listener = new EventoAutorizacaoKafkaListener(useCase);
    }

    private EventoAutorizacao eventoMinimo() {
        return EventoAutorizacao.newBuilder()
                .setIdAutorizacao(UUID.randomUUID())
                .setIdParticaoConta(950)
                .setDataFimVigencia(LocalDate.now())
                .setTipoProduto(1L)
                .setStatus(4)
                .setDataHoraInclusao(LocalDateTime.now())
                .setDataHoraUltimaAtlz(LocalDateTime.now())
                .setCodigoCanalContratacao("canal")
                .build();
    }

    @Test
    @DisplayName("processa com sucesso (AckMode.RECORD comita o offset ao retornar sem exceção)")
    void processaComSucesso() {
        inicializar();
        EventoAutorizacao evento = eventoMinimo();

        assertDoesNotThrow(() -> listener.escutar(evento));

        verify(useCase).processar(evento);
    }

    @Test
    @DisplayName("erro no processamento propaga a exceção (AckMode.RECORD não comita o offset)")
    void erroNoProcessamentoPropagaExcecao() {
        inicializar();
        EventoAutorizacao evento = eventoMinimo();
        doThrow(new RuntimeException("falha")).when(useCase).processar(evento);

        assertThrows(RuntimeException.class, () -> listener.escutar(evento));
    }

}
