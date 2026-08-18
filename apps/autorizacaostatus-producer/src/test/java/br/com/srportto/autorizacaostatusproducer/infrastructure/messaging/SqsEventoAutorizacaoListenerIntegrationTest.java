package br.com.srportto.autorizacaostatusproducer.infrastructure.messaging;

import br.com.srportto.autorizacaostatusproducer.domain.exception.EventoAutorizacaoKafkaIndisponivelException;
import br.com.srportto.autorizacaostatusproducer.domain.port.in.ProcessarEventoAutorizacaoUseCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Testes de integração do adaptador {@code @SqsListener} contra a fila real do Floci —
 * {@code useCase} é mockado para isolar do Kafka e exercitar só a ponte SQS (ack/retenção)
 * pelo pipeline real do Spring Cloud AWS. Validador e converter são beans reais: os bodies
 * enviados precisam ser JSON de verdade (D1 moveu a desserialização para este adaptador).
 *
 * <p>Requer o Floci no ar com {@code SQS-eventos-autorizacao} provisionada. Se o Floci não
 * estiver disponível no ambiente, a verificação empírica das tasks 5.5/5.6 (JSON malformado
 * -> DLQ/reentrega) é feita por
 * {@link br.com.srportto.autorizacaostatusproducer.infrastructure.messaging.SqsEventoAutorizacaoListenerTest},
 * que exercita a mesma classificação sem depender de infraestrutura externa.
 */
@SpringBootTest
@DisplayName("Testes de integração do SqsEventoAutorizacaoListener (Floci real)")
class SqsEventoAutorizacaoListenerIntegrationTest {

    private static final String MENSAGEM_VALIDA = "{"
            + "\"id_autorizacao\":\"550e8400-e29b-41d4-a716-446655440000\","
            + "\"id_particao_conta\":950,"
            + "\"data_fim_vigencia\":\"2027-01-01\","
            + "\"tipo_produto\":1,"
            + "\"status\":4,"
            + "\"data_hora_inclusao\":\"2026-07-26T10:00:00\","
            + "\"data_hora_ultima_atlz\":\"2026-07-26T10:00:00\","
            + "\"codigo_canal_contratacao\":\"canal\"}";

    /** Cliente usado só para provisionar/derrubar a fila dedicada — separado do bean da aplicação. */
    private static SqsAsyncClient clienteDeSetup;
    private static String queueUrlDedicada;

    /**
     * A suíte inteira reutilizava {@code SQS-eventos-autorizacao} — a fila compartilhada que o
     * container da própria aplicação também consome quando a orquestração local está no ar,
     * fazendo os dois disputarem a mesma mensagem. Cada execução desta classe agora recebe uma
     * fila só sua, criada antes do contexto Spring subir (o {@code @SqsListener} resolve o nome
     * da fila na inicialização).
     */
    @DynamicPropertySource
    static void queueDedicadaParaEsteTeste(DynamicPropertyRegistry registry) {
        clienteDeSetup = SqsAsyncClient.builder()
                .endpointOverride(URI.create("http://localhost:4566"))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build();

        String nomeFila = "SQS-eventos-autorizacao-it-" + UUID.randomUUID();
        queueUrlDedicada = clienteDeSetup.createQueue(CreateQueueRequest.builder()
                        .queueName(nomeFila)
                        .build())
                .join()
                .queueUrl();

        registry.add("sqs.queue-url", () -> queueUrlDedicada);
    }

    @AfterAll
    static void removerQueueDedicada() {
        clienteDeSetup.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrlDedicada).build()).join();
        clienteDeSetup.close();
    }

    @Value("${sqs.queue-url}")
    private String queueUrl;

    @Autowired
    private SqsAsyncClient sqsAsyncClient;

    @MockitoBean
    private ProcessarEventoAutorizacaoUseCase useCase;

    @BeforeEach
    @AfterEach
    void esvaziarFila() {
        for (int tentativa = 0; tentativa < 10; tentativa++) {
            List<Message> mensagens = sqsAsyncClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(0)
                    .visibilityTimeout(1)
                    .build()).join().messages();

            if (mensagens.isEmpty()) {
                return;
            }
            mensagens.forEach(m -> sqsAsyncClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(m.receiptHandle())
                    .build()).join());
        }
    }

    private void enviarMensagem(String body) {
        sqsAsyncClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .build()).join();
    }

    /** Soma visíveis + em voo: conta a mensagem em qualquer estado, exceto removida. */
    private long mensagensNaFila() {
        Map<QueueAttributeName, String> atributos = sqsAsyncClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                        .build())
                .join()
                .attributes();
        long visiveis = Long.parseLong(atributos.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
        long emVoo = Long.parseLong(atributos.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
        return visiveis + emVoo;
    }

    /**
     * Contagem por DELTA em relação à baseline, não valor absoluto: uma mensagem retryable
     * de outro teste fica em voo pelo visibility timeout inteiro (60s) e não é drenável
     * pelo {@code @BeforeEach}.
     */
    @Test
    @DisplayName("processamento bem-sucedido: mensagem é removida da fila (ack)")
    void processamentoComSucessoRemoveAMensagem() {
        long baseline = mensagensNaFila();

        enviarMensagem(MENSAGEM_VALIDA);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    verify(useCase, atLeast(1)).processar(any(), any());
                    assertEquals(baseline, mensagensNaFila());
                });
    }

    @Test
    @DisplayName("falha não-retryable: JSON malformado é confirmado (ack) já na primeira tentativa, "
            + "sem chegar ao use case — descarte consciente (task 5.3/5.5)")
    void falhaNaoRetryableDescartaAMensagem() {
        long baseline = mensagensNaFila();

        enviarMensagem("{isso nao e json valido");

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertEquals(baseline, mensagensNaFila()));
        verify(useCase, org.mockito.Mockito.never()).processar(any(), any());
    }

    @Test
    @DisplayName("falha retryable: mensagem NÃO é confirmada — continua contabilizada na fila")
    void falhaRetryableNaoDaAck() {
        doThrow(new EventoAutorizacaoKafkaIndisponivelException("kafka fora do ar", new RuntimeException()))
                .when(useCase).processar(any(), any());
        long baseline = mensagensNaFila();

        enviarMensagem(MENSAGEM_VALIDA);

        await().atMost(Duration.ofSeconds(15)).until(() -> {
            try {
                verify(useCase, atLeast(1)).processar(any(), any());
                return true;
            } catch (AssertionError e) {
                return false;
            }
        });

        // segue contabilizada na fila (visível ou em voo) — nunca removida
        assertEquals(baseline + 1, mensagensNaFila());
    }

}
