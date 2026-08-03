package br.com.srportto.autorizacaostatusproducer.entrypoint.sqs;

import br.com.srportto.autorizacaostatusproducer.application.eventos.ProcessarEventoAutorizacaoUseCase;
import br.com.srportto.autorizacaostatusproducer.shared.exceptions.EventoAutorizacaoInvalidoException;
import br.com.srportto.autorizacaostatusproducer.shared.exceptions.EventoAutorizacaoKafkaIndisponivelException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Testes de integração do adaptador {@code @SqsListener} contra a fila real do Floci —
 * o {@code useCase} é mockado para isolar o teste do Kafka/Schema Registry e exercitar
 * só a ponte SQS: ack/retenção conforme a classificação do
 * {@link SqsEventoAutorizacaoErrorInterceptor}, através do pipeline real do Spring Cloud
 * AWS (não chamadas diretas de método).
 *
 * <p>Requer o Floci no ar com a fila {@code SQS-eventos-autorizacao} provisionada (ver
 * {@code infra/envs/local-messaging}) — mesmo pré-requisito de {@code mvn spring-boot:run}.
 */
@SpringBootTest
@DisplayName("Testes de integração do SqsEventoAutorizacaoListener (Floci real)")
class SqsEventoAutorizacaoListenerIntegrationTest {

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
     * A contagem é feita por DELTA em relação à baseline capturada antes do envio — não
     * por valor absoluto. Uma mensagem retryable de outro teste desta classe fica em voo
     * (invisível) pelo visibility timeout inteiro da fila (60s, ver
     * {@code infra/envs/local-messaging}), tempo maior que a duração deste teste, e por
     * isso não é drenável pelo {@code @BeforeEach}. Um valor absoluto quebraria sob essa
     * pré-existência; o delta é robusto à ordem de execução dos testes.
     */
    @Test
    @DisplayName("processamento bem-sucedido: mensagem é removida da fila (ack)")
    void processamentoComSucessoRemoveAMensagem() {
        doNothing().when(useCase).processar(eq("evento-ok"));
        long baseline = mensagensNaFila();

        enviarMensagem("evento-ok");

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    verify(useCase, atLeast(1)).processar(eq("evento-ok"));
                    assertEquals(baseline, mensagensNaFila());
                });
    }

    @Test
    @DisplayName("falha não-retryable: mensagem é confirmada (ack) já na primeira tentativa — descarte consciente")
    void falhaNaoRetryableDescartaAMensagem() {
        doThrow(new EventoAutorizacaoInvalidoException("payload inválido", new RuntimeException()))
                .when(useCase).processar(eq("evento-invalido"));
        long baseline = mensagensNaFila();

        enviarMensagem("evento-invalido");

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    verify(useCase, atLeast(1)).processar(eq("evento-invalido"));
                    assertEquals(baseline, mensagensNaFila());
                });
    }

    @Test
    @DisplayName("falha retryable: mensagem NÃO é confirmada — continua contabilizada na fila")
    void falhaRetryableNaoDaAck() {
        doThrow(new EventoAutorizacaoKafkaIndisponivelException("kafka fora do ar", new RuntimeException()))
                .when(useCase).processar(eq("evento-kafka-indisponivel"));
        long baseline = mensagensNaFila();

        enviarMensagem("evento-kafka-indisponivel");

        await().atMost(Duration.ofSeconds(15)).until(() -> {
            try {
                verify(useCase, atLeast(1)).processar(eq("evento-kafka-indisponivel"));
                return true;
            } catch (AssertionError e) {
                return false;
            }
        });

        // a mensagem segue contabilizada na fila (visível ou em voo) — nunca removida,
        // mesmo após a tentativa de processamento
        assertEquals(baseline + 1, mensagensNaFila());
    }

}
