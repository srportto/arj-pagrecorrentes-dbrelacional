package br.com.srportto.eventosconsumer.infrastructure.kafka;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import br.com.srportto.eventosconsumer.application.eventos.ProcessarEventoAutorizacaoUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do EventoAutorizacaoKafkaListener")
class EventoAutorizacaoKafkaListenerTest {

    @Mock
    private ProcessarEventoAutorizacaoUseCase useCase;
    @Mock
    private Acknowledgment acknowledgment;

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
    @DisplayName("processa com sucesso e comita o offset (Acknowledgment.acknowledge) após o log")
    void processaComSucessoEComitaOffset() {
        inicializar();
        EventoAutorizacao evento = eventoMinimo();

        listener.escutar(evento, "CRIACAO", acknowledgment);

        verify(useCase).processar(evento, "CRIACAO");
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("erro no processamento não comita o offset")
    void erroNoProcessamentoNaoComitaOffset() {
        inicializar();
        EventoAutorizacao evento = eventoMinimo();
        doThrow(new RuntimeException("falha")).when(useCase).processar(evento, "CRIACAO");

        assertThrows(RuntimeException.class, () -> listener.escutar(evento, "CRIACAO", acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

}
