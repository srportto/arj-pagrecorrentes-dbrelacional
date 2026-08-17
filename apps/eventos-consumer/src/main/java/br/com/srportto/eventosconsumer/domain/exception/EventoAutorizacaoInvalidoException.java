package br.com.srportto.eventosconsumer.domain.exception;

/**
 * Evento consumido com dado que o domínio não reconhece (status desconhecido) — não-retryable,
 * nenhuma reentrega corrige um valor que o schema nunca vai produzir. Espelha o nome da exceção
 * equivalente em {@code autorizacaostatus-producer}.
 */
public class EventoAutorizacaoInvalidoException extends RuntimeException {

    public EventoAutorizacaoInvalidoException(String message) {
        super(message);
    }
}
