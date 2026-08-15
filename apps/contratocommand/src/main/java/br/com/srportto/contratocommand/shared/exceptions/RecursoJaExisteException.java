package br.com.srportto.contratocommand.shared.exceptions;

public class RecursoJaExisteException extends RuntimeException {

    // Chave de negócio duplicada → HTTP 409 no ApiExceptionHandler; distinta de BusinessException
    // (422), pois aqui o problema é o recurso já existir, não a regra em si.
    public RecursoJaExisteException(String message) {
        super(message);
    }

}
