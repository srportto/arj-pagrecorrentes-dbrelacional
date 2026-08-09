package br.com.srportto.temporizaautorizacao.entrypoint.sqs;

import br.com.srportto.temporizaautorizacao.application.agendamento.AgendarExpiracaoUseCase;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de ENTRADA: consome a fila via {@code @SqsListener} e delega ao use case, sem
 * regra de negócio própria — mesmo padrão do {@code autorizacaostatus-producer}.
 *
 * <p>Este método não faz {@code try/catch}: toda classificação de falha (ack ou retenção) é
 * responsabilidade exclusiva de {@link TemporizacaoEventoErrorInterceptor}.
 */
@Component
public class TemporizacaoEventoListener {

    private final AgendarExpiracaoUseCase useCase;

    public TemporizacaoEventoListener(AgendarExpiracaoUseCase useCase) {
        this.useCase = useCase;
    }

    @SqsListener(queueNames = "${sqs.queue-url}", factory = "temporizacaoSqsListenerContainerFactory")
    public void receber(String body) {
        useCase.agendar(body);
    }

}
