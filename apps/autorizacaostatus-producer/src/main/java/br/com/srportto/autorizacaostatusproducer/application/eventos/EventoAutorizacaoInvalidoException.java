package br.com.srportto.autorizacaostatusproducer.application.eventos;

/** Falha não-retryable (JSON/Avro inválido); o listener SQS loga e dá ack — descarte consciente, retry seria inútil. */
public class EventoAutorizacaoInvalidoException extends RuntimeException {

    public EventoAutorizacaoInvalidoException(String message, Throwable cause) {
        super(message, cause);
    }

}
