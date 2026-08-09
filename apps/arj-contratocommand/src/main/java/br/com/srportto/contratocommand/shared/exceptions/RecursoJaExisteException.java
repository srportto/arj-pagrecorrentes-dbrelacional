package br.com.srportto.contratocommand.shared.exceptions;

public class RecursoJaExisteException extends RuntimeException {

    // Lançada quando a requisição tentaria criar um recurso que já existe (chave de negócio
    // duplicada). Mapeada para HTTP 409 no ApiExceptionHandler — distinta de BusinessException
    // (422, violação de regra), pois aqui o problema é o recurso já existir, não o formato ou a
    // regra da requisição em si.
    public RecursoJaExisteException(String message) {
        super(message);
    }

}
