package br.com.srportto.autorizacaostatusproducer.infrastructure.messaging;

import br.com.srportto.autorizacaostatusproducer.domain.exception.EventoAutorizacaoInvalidoException;
import io.awspring.cloud.sqs.listener.errorhandler.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ponto único de classificação de falha do consumo — equivalente ao {@code ApiExceptionHandler}
 * do lado REST. Engolir a exceção confirma o ack; relançar mantém a mensagem na fila até o
 * visibility timeout. Nenhum log carrega o body (dado pessoal) — só o {@code messageId}.
 */
@Component
public class SqsEventoAutorizacaoErrorInterceptor implements ErrorHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(SqsEventoAutorizacaoErrorInterceptor.class);

    @Override
    public void handle(Message<String> message, Throwable t) {
        // MessageHeaders#getId() é anulável por contrato — não deixar a classificação falhar com
        // NPE antes de decidir ack/retenção por causa só do log.
        String messageId = Optional.ofNullable(message.getHeaders().getId())
                .map(UUID::toString)
                .orElse("desconhecido");

        if (contemEventoInvalido(t)) {
            log.error("Mensagem não-retryable descartada: messageId={}", messageId, t);
            return;
        }

        log.error("Falha ao processar a mensagem messageId={}. Não será confirmada — volta à fila "
                + "após o visibility timeout", messageId, t);
        throw relancavel(t);
    }

    /** ErrorHandler só relança RuntimeException — encapsula checked exception sem perder a causa. */
    private RuntimeException relancavel(Throwable t) {
        return t instanceof RuntimeException runtimeException ? runtimeException : new IllegalStateException(t);
    }

    /** Protege contra ciclo na cadeia de causas — o JDK só impede {@code cause == this}. */
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
