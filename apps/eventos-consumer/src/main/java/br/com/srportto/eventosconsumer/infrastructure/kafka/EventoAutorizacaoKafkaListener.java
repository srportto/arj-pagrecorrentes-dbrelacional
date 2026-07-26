package br.com.srportto.eventosconsumer.infrastructure.kafka;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import br.com.srportto.eventosconsumer.application.eventos.ProcessarEventoAutorizacaoUseCase;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consome o topico eventos-autorizacao com AckMode.MANUAL: o offset so avanca apos o
 * use case processar (log de sucesso) sem lancar excecao. Erro no processamento nao
 * comita o offset — a reentrega segue a semantica do DefaultErrorHandler do
 * spring-kafka (seek + retry), nao a de visibility timeout do SQS.
 */
@Component
public class EventoAutorizacaoKafkaListener {

    private final ProcessarEventoAutorizacaoUseCase useCase;

    public EventoAutorizacaoKafkaListener(ProcessarEventoAutorizacaoUseCase useCase) {
        this.useCase = useCase;
    }

    @KafkaListener(topics = "${kafka.topic}", groupId = "${kafka.group-id}",
            containerFactory = "eventoAutorizacaoKafkaListenerContainerFactory")
    public void escutar(@Payload EventoAutorizacao evento,
            @Header(name = "tipoEvento", required = false) String tipoEvento,
            Acknowledgment acknowledgment) {
        useCase.processar(evento, tipoEvento);
        acknowledgment.acknowledge();
    }

}
