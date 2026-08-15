package br.com.srportto.contratoquery.shared.exceptions;

public class ApplicationException extends RuntimeException {

	// Lançada quando ocorre um erro inesperado de sistema. Mapeada para HTTP 500 no ApiExceptionHandler.
	public ApplicationException(String message) {
		super(message);
	}

	public ApplicationException(String message, Throwable cause) {
		super(message, cause);
	}

}
