package br.com.srportto.contratocommand.shared.exceptions;

public class ApplicationException extends RuntimeException {

    // Lançada quando ocorre um erro inesperado de sistema. Mapeada para HTTP 500 no ApiExceptionHandler.
    public ApplicationException(String message) {
        super(message);
    }

}
