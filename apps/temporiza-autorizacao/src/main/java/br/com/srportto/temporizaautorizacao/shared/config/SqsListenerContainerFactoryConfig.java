package br.com.srportto.temporizaautorizacao.shared.config;

import br.com.srportto.temporizaautorizacao.entrypoint.sqs.TemporizacaoEventoErrorInterceptor;
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;

/** Configura o container do {@code @SqsListener}: concorrência, encerramento gracioso e o error handler. */
@Configuration
public class SqsListenerContainerFactoryConfig {

    private static final int MAX_CONCURRENT_MESSAGES = 10;
    private static final Duration LISTENER_SHUTDOWN_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration ACKNOWLEDGEMENT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(20);

    @Bean
    public SqsMessageListenerContainerFactory<String> temporizacaoSqsListenerContainerFactory(
            SqsAsyncClient sqsAsyncClient, TemporizacaoEventoErrorInterceptor errorInterceptor) {
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
