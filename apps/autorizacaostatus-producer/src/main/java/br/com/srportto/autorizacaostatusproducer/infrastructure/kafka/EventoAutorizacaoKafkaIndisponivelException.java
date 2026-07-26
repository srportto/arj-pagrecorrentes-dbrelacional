package br.com.srportto.autorizacaostatusproducer.infrastructure.kafka;

/**
 * Falha retryable ao produzir no Kafka (broker/Schema Registry indisponivel, timeout).
 * O chamador (listener SQS) NAO SHALL dar ack na mensagem quando esta excecao propaga.
 */
public class EventoAutorizacaoKafkaIndisponivelException extends RuntimeException {

    public EventoAutorizacaoKafkaIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }

}
