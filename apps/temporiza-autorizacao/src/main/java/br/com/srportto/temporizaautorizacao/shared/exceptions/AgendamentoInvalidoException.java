package br.com.srportto.temporizaautorizacao.shared.exceptions;

/** Payload de recepção malformado ou sem os campos necessários ao agendamento — não-retryable. */
public class AgendamentoInvalidoException extends RuntimeException {

    public AgendamentoInvalidoException(String message) {
        super(message);
    }

    public AgendamentoInvalidoException(String message, Throwable cause) {
        super(message, cause);
    }

}
