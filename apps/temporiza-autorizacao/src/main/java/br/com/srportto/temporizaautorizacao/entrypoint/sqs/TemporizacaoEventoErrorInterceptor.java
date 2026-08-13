package br.com.srportto.temporizaautorizacao.entrypoint.sqs;

import br.com.srportto.temporizaautorizacao.shared.exceptions.AgendamentoInvalidoException;
import io.awspring.cloud.sqs.listener.errorhandler.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Ponto único de classificação de falha: engolir a exceção acka; relançar mantém sem ack (retorna após visibility timeout). */
@Component
public class TemporizacaoEventoErrorInterceptor implements ErrorHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(TemporizacaoEventoErrorInterceptor.class);

    @Override
    public void handle(Message<String> message, Throwable t) {
        String messageId = message.getHeaders().getId().toString();

        if (contemAgendamentoInvalido(t)) {
            log.error("Mensagem não-retryable descartada: messageId={}", messageId, t);
            return;
        }

        log.error("Falha ao agendar a partir da mensagem messageId={}. Não será confirmada — volta à fila "
                + "após o visibility timeout", messageId, t);
        throw relancavel(t);
    }

    private RuntimeException relancavel(Throwable t) {
        return t instanceof RuntimeException runtimeException ? runtimeException : new IllegalStateException(t);
    }

    private boolean contemAgendamentoInvalido(Throwable t) {
        Set<Throwable> visitados = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable atual = t; atual != null && visitados.add(atual); atual = atual.getCause()) {
            if (atual instanceof AgendamentoInvalidoException) {
                return true;
            }
        }
        return false;
    }

}
