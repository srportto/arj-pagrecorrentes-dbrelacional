package br.com.srportto.autorizacaostatusproducer.shared.exceptions;

/**
 * Falha não-retryable (JSON/Avro inválido, campo obrigatório ausente, status desconhecido);
 * o listener SQS loga e dá ack — descarte consciente, retry seria inútil.
 *
 * <p>A mensagem NUNCA deve conter o body do payload: ele carrega dado pessoal
 * (id_pessoa_pagadora/devedora/recebedora, valor, descricao, metadados) e a exceção é
 * logada com stack trace.
 */
public class EventoAutorizacaoInvalidoException extends RuntimeException {

    public EventoAutorizacaoInvalidoException(String message) {
        super(message);
    }

    public EventoAutorizacaoInvalidoException(String message, Throwable cause) {
        super(message, cause);
    }

}
