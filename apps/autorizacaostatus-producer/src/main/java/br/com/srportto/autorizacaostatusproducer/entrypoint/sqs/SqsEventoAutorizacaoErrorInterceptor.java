package br.com.srportto.autorizacaostatusproducer.entrypoint.sqs;

import br.com.srportto.autorizacaostatusproducer.shared.exceptions.EventoAutorizacaoInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Ponto único de classificação de falha do consumo da fila — equivalente ao
 * {@code ApiExceptionHandler} do lado REST, mas para o escopo de mensageria: toda
 * exceção lançada pelo processamento de uma mensagem passa por aqui antes de o
 * listener decidir dar ack ou deixar a mensagem retornar à fila.
 *
 * <p>Nenhum log daqui carrega o body da mensagem: o payload contém dado pessoal. A
 * mensagem é identificada pelo {@code messageId} do SQS.
 */
@Component
public class SqsEventoAutorizacaoErrorInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SqsEventoAutorizacaoErrorInterceptor.class);

    /**
     * @return {@code true} se a mensagem deve ser confirmada (ack) — descarte
     *         consciente de falha não-retryable; {@code false} se deve permanecer na
     *         fila para nova tentativa após o visibility timeout.
     */
    public boolean tratar(Message message, Exception e) {
        if (e instanceof EventoAutorizacaoInvalidoException) {
            log.error("Mensagem não-retryable descartada: messageId={}", message.messageId(), e);
            return true;
        }

        log.error("Falha ao processar a mensagem messageId={}. Não será confirmada — volta à fila "
                + "após o visibility timeout", message.messageId(), e);
        return false;
    }

}
