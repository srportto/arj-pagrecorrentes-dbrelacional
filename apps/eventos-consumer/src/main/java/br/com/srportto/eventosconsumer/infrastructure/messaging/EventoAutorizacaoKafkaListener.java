package br.com.srportto.eventosconsumer.infrastructure.messaging;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import br.com.srportto.eventosconsumer.domain.port.in.ProcessarEventoAutorizacaoUseCase;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Consome o tópico com AckMode.RECORD: offset só avança após o use case processar sem lançar exceção. */
@Component
public class EventoAutorizacaoKafkaListener {

    private final ProcessarEventoAutorizacaoUseCase useCase;

    public EventoAutorizacaoKafkaListener(ProcessarEventoAutorizacaoUseCase useCase) {
        this.useCase = useCase;
    }

    @KafkaListener(topics = "${kafka.topic}", groupId = "${kafka.group-id}",
            containerFactory = "eventoAutorizacaoKafkaListenerContainerFactory")
    public void escutar(@Payload EventoAutorizacao evento) {
        useCase.processar(evento);
    }

}
