package br.com.srportto.eventosconsumer.infrastructure.kafka;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import br.com.srportto.eventosconsumer.application.eventos.ProcessarEventoAutorizacaoUseCase;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Consome o tópico com AckMode.MANUAL: offset só avança após o use case processar sem lançar exceção. */
@Component
public class EventoAutorizacaoKafkaListener {

    private final ProcessarEventoAutorizacaoUseCase useCase;

    public EventoAutorizacaoKafkaListener(ProcessarEventoAutorizacaoUseCase useCase) {
        this.useCase = useCase;
    }

    @KafkaListener(topics = "${kafka.topic}", groupId = "${kafka.group-id}",
            containerFactory = "eventoAutorizacaoKafkaListenerContainerFactory")
    public void escutar(@Payload EventoAutorizacao evento, Acknowledgment acknowledgment) {
        useCase.processar(evento);
        acknowledgment.acknowledge();
    }

}
