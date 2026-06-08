package br.com.srportto.contratocommand.shared.exceptions;

public class BusinessException extends RuntimeException {

    // toda vez que uma regra de negocios for violada, deve ser lançada uma
    // BusinessException, com a mensagem explicando o motivo da violacao da regra de
    // negocio
    // exemplo: tentativa de criar um recurso com um estado inválido, tentativa de
    // realizar uma
    public BusinessException(String message) {
        super(message);
    }

}
