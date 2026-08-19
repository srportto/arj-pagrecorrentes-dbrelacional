package br.com.srportto.eventosconsumer.infrastructure.messaging;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import br.com.srportto.eventosconsumer.domain.model.EventoAutorizacaoConsumido;
import br.com.srportto.eventosconsumer.domain.port.in.ProcessarEventoAutorizacaoUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
    @DisplayName("processa com sucesso (AckMode.RECORD comita o offset ao retornar sem exceção), "
            + "traduzindo o Avro para o modelo de domínio antes de chamar o use case")
    void processaComSucesso() {
        inicializar();
        EventoAutorizacao evento = eventoMinimo();
        EventoAutorizacaoConsumido esperado =
                new EventoAutorizacaoConsumido(evento.getIdAutorizacao(), evento.getStatus());

        assertDoesNotThrow(() -> listener.escutar(evento));

        verify(useCase).processar(eq(esperado));
    }

    @Test
    @DisplayName("erro no processamento propaga a exceção (AckMode.RECORD não comita o offset)")
    void erroNoProcessamentoPropagaExcecao() {
        inicializar();
        EventoAutorizacao evento = eventoMinimo();
        EventoAutorizacaoConsumido esperado =
                new EventoAutorizacaoConsumido(evento.getIdAutorizacao(), evento.getStatus());
        doThrow(new RuntimeException("falha")).when(useCase).processar(eq(esperado));

        assertThrows(RuntimeException.class, () -> listener.escutar(evento));
    }

    @AfterEach
    void limpaMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("MDC é limpo após o processamento, com sucesso ou falha")
    void mdcELimpoAposProcessamento() {
        inicializar();
        EventoAutorizacao evento = eventoMinimo();

        listener.escutar(evento);
        assertNull(MDC.get("traceId"), "MDC deve ser limpo após sucesso");

        EventoAutorizacaoConsumido esperado =
                new EventoAutorizacaoConsumido(evento.getIdAutorizacao(), evento.getStatus());
        doThrow(new RuntimeException("falha")).when(useCase).processar(eq(esperado));
        assertThrows(RuntimeException.class, () -> listener.escutar(evento));
        assertNull(MDC.get("traceId"), "MDC deve ser limpo mesmo após falha");
    }

    @Test
    @DisplayName("um traceId é populado no MDC durante o processamento do registro")
    void traceIdEPopuladoDuranteOProcessamento() {
        inicializar();
        EventoAutorizacao evento = eventoMinimo();
        EventoAutorizacaoConsumido esperado =
                new EventoAutorizacaoConsumido(evento.getIdAutorizacao(), evento.getStatus());
        doAnswer(invocation -> {
            assertTrue(MDC.get("traceId") != null && !MDC.get("traceId").isBlank());
            return null;
        }).when(useCase).processar(eq(esperado));

        listener.escutar(evento);

        verify(useCase).processar(eq(esperado));
    }

}
