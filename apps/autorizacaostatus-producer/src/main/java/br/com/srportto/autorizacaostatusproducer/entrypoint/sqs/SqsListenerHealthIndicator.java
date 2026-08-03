package br.com.srportto.autorizacaostatusproducer.entrypoint.sqs;

import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reflete no /actuator/health o estado real do consumo da fila, via
 * {@link MessageListenerContainerRegistry} do Spring Cloud AWS.
 *
 * <p>{@code registry.isRunning()} equivale à antiga flag {@code SmartLifecycle} do
 * listener manual: falso durante shutdown intencional. Já o {@code isRunning()} de cada
 * container individual equivale à antiga liveness da thread de polling — um container
 * parado enquanto o registry ainda está ativo é um outage que não deve passar
 * despercebido, assim como a thread morta era antes.
 *
 * <p>Listener parado NÃO é falha: durante o shutdown a parada é intencional.
 */
@Component("sqsListener")
public class SqsListenerHealthIndicator implements HealthIndicator {

    private final MessageListenerContainerRegistry registry;

    public SqsListenerHealthIndicator(MessageListenerContainerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        if (!registry.isRunning()) {
            return Health.up().withDetail("estado", "parado").build();
        }

        boolean algumContainerParado = registry.getListenerContainers().stream()
                .anyMatch(container -> !container.isRunning());

        if (algumContainerParado) {
            return Health.down()
                    .withDetail("estado", "ativo")
                    .withDetail("container", "parado")
                    .build();
        }

        return Health.up()
                .withDetail("estado", "ativo")
                .withDetail("container", "em execucao")
                .build();
    }

}
