package br.com.srportto.autorizacaostatusproducer.entrypoint.sqs;

import br.com.srportto.autorizacaostatusproducer.application.eventos.ProcessarEventoAutorizacaoUseCase;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de ENTRADA: consome a fila via {@code @SqsListener} e delega ao use case.
 *
 * <p>Ack não é explícito: retorno normal = container confirma a mensagem. Como o use
 * case produz no Kafka de forma síncrona antes de retornar, o ack fica condicionado à
 * confirmação do broker (at-least-once).
 *
 * <p>Sem {@code try/catch} de propósito: classificação de falha é responsabilidade
 * exclusiva de {@link SqsEventoAutorizacaoErrorInterceptor} — duplicar aqui reintroduz
 * o espalhamento que o interceptor existe para evitar.
 */
@Component
public class SqsEventoAutorizacaoListener {

    private final ProcessarEventoAutorizacaoUseCase useCase;

    public SqsEventoAutorizacaoListener(ProcessarEventoAutorizacaoUseCase useCase) {
        this.useCase = useCase;
    }

    @SqsListener(queueNames = "${sqs.queue-url}", factory = "eventosAutorizacaoSqsListenerContainerFactory")
    public void receber(String body) {
        useCase.processar(body);
    }

}
