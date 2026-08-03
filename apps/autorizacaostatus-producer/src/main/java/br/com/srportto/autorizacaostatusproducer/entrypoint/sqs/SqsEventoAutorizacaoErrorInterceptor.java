package br.com.srportto.autorizacaostatusproducer.entrypoint.sqs;

import br.com.srportto.autorizacaostatusproducer.shared.exceptions.EventoAutorizacaoInvalidoException;
import io.awspring.cloud.sqs.listener.errorhandler.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Ponto único de classificação de falha do consumo da fila — equivalente ao
 * {@code ApiExceptionHandler} do lado REST, mas para o escopo de mensageria: registrado
 * como error handler central do container (nunca duplicado em {@code catch} dentro do
 * método {@code @SqsListener}).
 *
 * <p>O contrato inverte em relação ao listener manual anterior: engolir a exceção
 * (retornar normalmente) faz o container tratar a mensagem como recuperada e confirmar
 * o ack; relançar mantém a mensagem sem ack, retornando à fila após o visibility
 * timeout. Não há retorno booleano interpretado por outra classe.
 *
 * <p>Nenhum log daqui carrega o body da mensagem: o payload contém dado pessoal. A
 * mensagem é identificada pelo {@code messageId} do SQS — preservado como o
 * {@code MessageHeaders.ID} da mensagem pelo conversor do Spring Cloud AWS.
 */
@Component
public class SqsEventoAutorizacaoErrorInterceptor implements ErrorHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(SqsEventoAutorizacaoErrorInterceptor.class);

    @Override
    public void handle(Message<String> message, Throwable t) {
        String messageId = message.getHeaders().getId().toString();

        if (contemEventoInvalido(t)) {
            log.error("Mensagem não-retryable descartada: messageId={}", messageId, t);
            return;
        }

        log.error("Falha ao processar a mensagem messageId={}. Não será confirmada — volta à fila "
                + "após o visibility timeout", messageId, t);
        throw relancavel(t);
    }

    /**
     * O framework espera que exceções relançadas por um {@code ErrorHandler} sejam
     * {@code RuntimeException} — encapsula qualquer checked exception sem perder a causa.
     */
    private RuntimeException relancavel(Throwable t) {
        return t instanceof RuntimeException runtimeException ? runtimeException : new IllegalStateException(t);
    }

    /**
     * Percorre a cadeia protegendo contra ciclo — o JDK só impede {@code cause == this}.
     */
    private boolean contemEventoInvalido(Throwable t) {
        Set<Throwable> visitados = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable atual = t; atual != null && visitados.add(atual); atual = atual.getCause()) {
            if (atual instanceof EventoAutorizacaoInvalidoException) {
                return true;
            }
        }
        return false;
    }

}
