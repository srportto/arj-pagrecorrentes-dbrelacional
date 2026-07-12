package br.com.srportto.contratoquery.shared.exceptions;

public class ResourceNotFoundException extends RuntimeException {

	// Lançada quando um recurso solicitado não existe. Mapeada para HTTP 404 no ApiExceptionHandler.
	public ResourceNotFoundException(String message) {
		super(message);
	}

}
