package br.com.srportto.contratoquery.domain.exception;

public class BusinessException extends RuntimeException {

	// Lançada quando uma regra de negócio é violada. Mapeada para HTTP 422 no ApiExceptionHandler.
	public BusinessException(String message) {
		super(message);
	}

	public BusinessException(String message, Throwable cause) {
		super(message, cause);
	}

}
