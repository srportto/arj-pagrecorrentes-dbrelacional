package br.com.srportto.autorizacaostatusproducer.entrypoint.sqs;

import br.com.srportto.autorizacaostatusproducer.shared.exceptions.EventoAutorizacaoInvalidoException;
import br.com.srportto.autorizacaostatusproducer.application.eventos.ProcessarEventoAutorizacaoUseCase;
import br.com.srportto.autorizacaostatusproducer.shared.config.AwsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do SqsEventoAutorizacaoListener")
class SqsEventoAutorizacaoListenerTest {

    private static final String QUEUE_URL = "http://localhost:4566/000000000000/SQS-eventos-autorizacao";

    @Mock
    private SqsClient sqsClient;
    @Mock
    private ProcessarEventoAutorizacaoUseCase useCase;

    private SqsEventoAutorizacaoListener listener;

    private void inicializar() {
        AwsProperties properties = new AwsProperties(null, "us-east-1", null, null, new AwsProperties.Sqs(QUEUE_URL));
        listener = new SqsEventoAutorizacaoListener(sqsClient, properties, useCase,
                new SqsEventoAutorizacaoErrorInterceptor());
    }

    private Message mensagem(String id, String body) {
        return Message.builder().messageId(id).receiptHandle("receipt-" + id).body(body).build();
    }

    @Test
    @DisplayName("processa com sucesso e dá ack (DeleteMessage) somente após a produção no Kafka")
    void processaComSucessoEDaAck() {
        inicializar();
        Message msg = mensagem("m1", "{\"id_autorizacao\":\"x\"}");

        listener.processarEDarAck(QUEUE_URL, msg);

        verify(useCase).processar(msg.body());
        verify(sqsClient).deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .receiptHandle("receipt-m1")
                .build());
    }

    @Test
    @DisplayName("falha retryable (ex.: Kafka indisponível) não dá ack — mensagem permanece na fila")
    void falhaRetryableNaoDaAck() {
        inicializar();
        Message msg = mensagem("m2", "{\"id_autorizacao\":\"x\"}");
        doThrow(new RuntimeException("kafka fora do ar")).when(useCase).processar(msg.body());

        listener.processarEDarAck(QUEUE_URL, msg);

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("falha não-retryable (mensagem inválida) loga e dá ack — descarte consciente")
    void falhaNaoRetryableDescartaComAck() {
        inicializar();
        Message msg = mensagem("m3", "{isso nao e json");
        doThrow(new EventoAutorizacaoInvalidoException("inválido", new RuntimeException()))
                .when(useCase).processar(msg.body());

        listener.processarEDarAck(QUEUE_URL, msg);

        verify(sqsClient).deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .receiptHandle("receipt-m3")
                .build());
    }

    @Test
    @DisplayName("pollOnce processa todas as mensagens recebidas, sem solicitar message attributes")
    void pollOnceProcessaMensagensRecebidas() {
        inicializar();
        Message m1 = mensagem("m1", "{\"a\":1}");
        Message m2 = mensagem("m2", "{\"a\":2}");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(m1, m2).build());

        listener.pollOnce();

        verify(useCase, times(1)).processar(m1.body());
        verify(useCase, times(1)).processar(m2.body());
        verify(sqsClient, times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("falha no ReceiveMessage não propaga exceção (aplica backoff internamente)")
    void falhaNoReceiveNaoPropaga() {
        inicializar();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(new RuntimeException("Floci fora do ar"));

        // interrompe a própria thread de teste para que o backoff (Thread.sleep) retorne
        // imediatamente via InterruptedException, sem esperar os 5s reais
        Thread.currentThread().interrupt();

        assertDoesNotThrow(() -> listener.pollOnce());
        verify(useCase, never()).processar(any());

        // limpa o status de interrupcao para nao vazar para os proximos testes
        Thread.interrupted();
    }

    @Test
    @DisplayName("isRunning reflete o estado após start()/stop()")
    void isRunningRefleteEstado() {
        inicializar();
        assertFalse(listener.isRunning());

        listener.start();
        assertTrue(listener.isRunning());

        listener.stop();
        assertFalse(listener.isRunning());
    }

    @Test
    @DisplayName("stop() aguarda a thread de polling encerrar antes de retornar")
    void stopAguardaThreadDePollingEncerrar() {
        inicializar();
        listener.start();
        assertTrue(listener.isThreadDePollingViva());

        listener.stop();

        // sem o join() em stop(), a thread ainda estaria viva aqui e o contexto Spring
        // fecharia SqsClient/Producer com uma mensagem possivelmente em voo
        assertFalse(listener.isThreadDePollingViva());
    }

    @Test
    @DisplayName("stop() sem start() prévio não quebra")
    void stopSemStartNaoQuebra() {
        inicializar();

        assertDoesNotThrow(() -> listener.stop());
    }

    @Test
    @DisplayName("Error no ciclo de consumo não encerra a thread de polling em silêncio")
    void errorNoCicloNaoEncerraAThreadDePolling() throws InterruptedException {
        inicializar();
        CountDownLatch recebeuChamada = new CountDownLatch(1);
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocacao -> {
            recebeuChamada.countDown();
            throw new StackOverflowError("Error simulado no ciclo de consumo");
        });

        listener.start();
        try {
            assertTrue(recebeuChamada.await(5, TimeUnit.SECONDS), "receiveMessage deveria ter sido chamado");
            // sem o catch (Throwable) a thread morre logo apos o Error; espera por condicao
            // (em vez de sleep fixo) para nao ficar frageis em CI lento
            assertTrue(permaneceVivaPor(Duration.ofSeconds(1)),
                    "a thread de polling deveria continuar viva após o Error");
        } finally {
            listener.stop();
        }
    }

    /** Verifica que a thread segue viva durante toda a janela, sem depender de um sleep fixo. */
    private boolean permaneceVivaPor(Duration janela) throws InterruptedException {
        long limite = System.nanoTime() + janela.toNanos();
        while (System.nanoTime() < limite) {
            if (!listener.isThreadDePollingViva()) {
                return false;
            }
            Thread.sleep(20);
        }
        return listener.isThreadDePollingViva();
    }

}
