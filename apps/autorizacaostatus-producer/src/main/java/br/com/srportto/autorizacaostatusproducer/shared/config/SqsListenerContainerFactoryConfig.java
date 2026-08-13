package br.com.srportto.autorizacaostatusproducer.shared.config;

import br.com.srportto.autorizacaostatusproducer.entrypoint.sqs.SqsEventoAutorizacaoErrorInterceptor;
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;

/**
 * Configura o container do {@code @SqsListener}: concorrência, shutdown gracioso e o
 * error handler central.
 *
 * <p>maxConcurrentMessages=10 roda em platform threads, não virtual — o pipeline de
 * execução do listener nesta versão do Spring Cloud AWS exige threads da sua própria
 * factory interna. Ponto de partida para calibração, não teto definitivo.
 *
 * <p>Os timeouts de shutdown garantem que o contexto só destrói SqsAsyncClient/Producer
 * Kafka depois que as execuções em voo terminam ou o tempo se esgota.
 */
@Configuration
public class SqsListenerContainerFactoryConfig {

    private static final int MAX_CONCURRENT_MESSAGES = 10;
    private static final Duration LISTENER_SHUTDOWN_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration ACKNOWLEDGEMENT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(20);

    @Bean
    public SqsMessageListenerContainerFactory<String> eventosAutorizacaoSqsListenerContainerFactory(
            SqsAsyncClient sqsAsyncClient, SqsEventoAutorizacaoErrorInterceptor errorInterceptor) {
        return SqsMessageListenerContainerFactory.<String>builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options
                        .acknowledgementMode(AcknowledgementMode.ON_SUCCESS)
                        .maxConcurrentMessages(MAX_CONCURRENT_MESSAGES)
                        .listenerShutdownTimeout(LISTENER_SHUTDOWN_TIMEOUT)
                        .acknowledgementShutdownTimeout(ACKNOWLEDGEMENT_SHUTDOWN_TIMEOUT))
                .errorHandler(errorInterceptor)
                .build();
    }

}
