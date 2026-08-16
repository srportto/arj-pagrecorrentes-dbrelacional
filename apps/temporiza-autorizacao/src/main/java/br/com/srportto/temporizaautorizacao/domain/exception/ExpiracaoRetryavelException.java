package br.com.srportto.temporizaautorizacao.domain.exception;

/** Falha ao acionar o command (5xx, timeout, erro de conexão) — o trabalho não é confirmado, fica no PEL do stream. */
public class ExpiracaoRetryavelException extends RuntimeException {

    public ExpiracaoRetryavelException(String message, Throwable cause) {
        super(message, cause);
    }

}
