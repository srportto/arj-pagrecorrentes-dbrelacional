package br.com.srportto.temporizaautorizacao.infrastructure.config;

import br.com.srportto.temporizaautorizacao.infrastructure.messaging.TemporizacaoEventoErrorInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do SqsListenerContainerFactoryConfig")
class SqsListenerContainerFactoryConfigTest {

    @Mock
    private SqsAsyncClient sqsAsyncClient;
    @Mock
    private TemporizacaoEventoErrorInterceptor errorInterceptor;

    @Test
    @DisplayName("temporizacaoSqsListenerContainerFactory constrói a factory com o error handler configurado")
    void constroiFactory() {
        var config = new SqsListenerContainerFactoryConfig();

        assertNotNull(config.temporizacaoSqsListenerContainerFactory(sqsAsyncClient, errorInterceptor));
    }

}
