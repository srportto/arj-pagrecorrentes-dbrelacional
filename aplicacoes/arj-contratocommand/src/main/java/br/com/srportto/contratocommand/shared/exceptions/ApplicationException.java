package br.com.srportto.contratocommand.shared.exceptions;

public class ApplicationException extends RuntimeException {

    // toda vez ocorra um erro esperado na aplicação, deve ser lançada uma
    // ApplicationException, com a mensagem explicando o motivo do erro
    // exemplo: falha na comunicação com um serviço externo, falha na leitura de um
    // arquivo, etc
    public ApplicationException(String message) {
        super(message);
    }

}
