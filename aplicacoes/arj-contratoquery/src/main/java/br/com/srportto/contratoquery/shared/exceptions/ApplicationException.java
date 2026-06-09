package br.com.srportto.contratoquery.shared.exceptions;

public class ApplicationException extends RuntimeException {

	// toda vez que ocorra um erro inesperado de sistema, deve ser lancada uma
	// ApplicationException, com a mensagem explicando o motivo do erro.
	// Mapeada para HTTP 500 no ApiExceptionHandler.
	public ApplicationException(String message) {
		super(message);
	}

}
