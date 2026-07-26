package br.com.srportto.autorizacaostatusproducer.application.eventos;

/**
 * Falha nao-retryable: o body da mensagem SQS nao pode ser desserializado ou convertido
 * para o schema Avro. O chamador (listener SQS) SHALL logar o body completo e dar ack
 * (descarte consciente) quando esta excecao propaga — retry seria inutil.
 */
public class EventoAutorizacaoInvalidoException extends RuntimeException {

    public EventoAutorizacaoInvalidoException(String message, Throwable cause) {
        super(message, cause);
    }

}
