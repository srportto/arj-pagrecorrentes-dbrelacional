package br.com.srportto.contratoquery.shared.exceptions;

public class BusinessException extends RuntimeException {

	// toda vez que uma regra de negocio for violada, deve ser lancada uma
	// BusinessException, com a mensagem explicando o motivo da violacao.
	// Mapeada para HTTP 422 no ApiExceptionHandler.
	public BusinessException(String message) {
		super(message);
	}

}
