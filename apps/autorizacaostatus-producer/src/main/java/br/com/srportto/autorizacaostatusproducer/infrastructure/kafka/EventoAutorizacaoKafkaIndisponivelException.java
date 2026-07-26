package br.com.srportto.autorizacaostatusproducer.infrastructure.kafka;

/** Falha retryable ao produzir no Kafka (broker/Schema Registry indisponível, timeout); o listener SQS não dá ack. */
public class EventoAutorizacaoKafkaIndisponivelException extends RuntimeException {

    public EventoAutorizacaoKafkaIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }

}
