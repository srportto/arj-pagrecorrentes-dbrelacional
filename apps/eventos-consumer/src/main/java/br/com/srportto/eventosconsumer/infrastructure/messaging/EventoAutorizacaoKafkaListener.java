package br.com.srportto.eventosconsumer.infrastructure.messaging;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import br.com.srportto.eventosconsumer.domain.port.in.ProcessarEventoAutorizacaoUseCase;
import java.util.UUID;
import org.slf4j.MDC;
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

    // group-id vem só do ConsumerFactory (KafkaConsumerConfig, via kafka.group-id) — não duplicado
    // aqui, para não ter dois caminhos de configuração para o mesmo valor.
    @KafkaListener(topics = "${kafka.topic}", containerFactory = "eventoAutorizacaoKafkaListenerContainerFactory")
    public void escutar(@Payload EventoAutorizacao evento) {
        // traceId por registro: MDC e por thread do container, sempre limpo no finally.
        MDC.put("traceId", UUID.randomUUID().toString());
        try {
            useCase.processar(EventoAutorizacaoAvroMapper.paraDominio(evento));
        } finally {
            MDC.clear();
        }
    }

}
