package br.com.srportto.autorizacaostatusproducer.shared.config;

import br.com.srportto.autorizacaostatusproducer.entrypoint.sqs.SqsEventoAutorizacaoErrorInterceptor;
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;

/**
 * Configura o container do {@code @SqsListener}: concorrencia, encerramento gracioso e
 * o ponto unico de classificacao de falha (erro do use case OU falha sincrona do
 * produce Kafka dentro dele).
 *
 * <p>maxConcurrentMessages=10 (default do framework): concorrencia real por instancia,
 * em vez do processamento serial do listener manual anterior. O container dimensiona
 * automaticamente um pool de platform threads proporcional a esse valor (nao virtual
 * threads — o pipeline de execucao do listener desta versao do Spring Cloud AWS exige
 * threads criadas pela sua propria factory interna). Ponto de partida para calibracao
 * por medicao de throughput, nao um teto definitivo.
 *
 * <p>listenerShutdownTimeout/acknowledgementShutdownTimeout substituem o join() manual
 * do listener anterior: o contexto Spring so destroi o SqsAsyncClient e o Producer
 * Kafka depois que as execucoes em voo terminam ou esse tempo se esgota.
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
