package br.com.srportto.contratocommand.shared.exceptions;

public class BusinessException extends RuntimeException {

    // Lançada quando uma regra de negócio é violada. Mapeada para HTTP 422 no ApiExceptionHandler.
    public BusinessException(String message) {
        super(message);
    }

}
