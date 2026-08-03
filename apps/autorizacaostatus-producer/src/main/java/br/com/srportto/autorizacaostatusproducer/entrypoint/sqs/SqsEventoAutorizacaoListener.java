package br.com.srportto.autorizacaostatusproducer.entrypoint.sqs;

import br.com.srportto.autorizacaostatusproducer.application.eventos.ProcessarEventoAutorizacaoUseCase;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de ENTRADA: consome a fila via {@code @SqsListener} (Spring Cloud AWS) e
 * delega o body ao use case, sem regra de negócio própria.
 *
 * <p>O ack não é explícito: o retorno normal do método é o que o container interpreta
 * como sucesso e confirma a mensagem — e como {@link ProcessarEventoAutorizacaoUseCase}
 * produz no Kafka de forma síncrona antes de retornar, o ack continua condicionado à
 * confirmação do broker, preservando a garantia at-least-once. Uma exceção propagada
 * mantém a mensagem na fila.
 *
 * <p>Este método não faz {@code try/catch}: toda classificação de falha (ack ou
 * retenção) é responsabilidade exclusiva de {@link SqsEventoAutorizacaoErrorInterceptor},
 * registrado como error handler da
 * {@code eventosAutorizacaoSqsListenerContainerFactory}. Duplicar essa decisão aqui
 * reintroduziria o espalhamento que o interceptor existe para evitar.
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
