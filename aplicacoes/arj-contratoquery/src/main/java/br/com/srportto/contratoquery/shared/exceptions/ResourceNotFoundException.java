package br.com.srportto.contratoquery.shared.exceptions;

public class ResourceNotFoundException extends RuntimeException {

	// lancada quando um recurso solicitado nao existe.
	// Mapeada para HTTP 404 no ApiExceptionHandler.
	public ResourceNotFoundException(String message) {
		super(message);
	}

}
