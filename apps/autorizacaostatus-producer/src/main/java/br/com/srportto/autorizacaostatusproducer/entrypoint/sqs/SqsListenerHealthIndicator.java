package br.com.srportto.autorizacaostatusproducer.entrypoint.sqs;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reflete no /actuator/health a liveness da thread de polling do listener SQS.
 *
 * <p>Sem este indicador a aplicação reporta UP mesmo com o consumidor morto: a flag
 * {@code running} do SmartLifecycle continua {@code true} se a thread de polling morrer,
 * e nada mais observa esse estado — um outage completo e silencioso.
 *
 * <p>Listener parado NÃO é falha: durante o shutdown a parada é intencional.
 */
@Component("sqsListener")
public class SqsListenerHealthIndicator implements HealthIndicator {

    private final SqsEventoAutorizacaoListener listener;

    public SqsListenerHealthIndicator(SqsEventoAutorizacaoListener listener) {
        this.listener = listener;
    }

    @Override
    public Health health() {
        if (!listener.isRunning()) {
            return Health.up().withDetail("estado", "parado").build();
        }

        if (!listener.isThreadDePollingViva()) {
            return Health.down()
                    .withDetail("estado", "ativo")
                    .withDetail("threadDePolling", "morta")
                    .build();
        }

        return Health.up()
                .withDetail("estado", "ativo")
                .withDetail("threadDePolling", "viva")
                .build();
    }

}
