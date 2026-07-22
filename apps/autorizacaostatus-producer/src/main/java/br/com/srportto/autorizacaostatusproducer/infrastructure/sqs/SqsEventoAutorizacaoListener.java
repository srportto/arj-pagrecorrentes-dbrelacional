package br.com.srportto.autorizacaostatusproducer.infrastructure.sqs;

import br.com.srportto.autorizacaostatusproducer.application.eventos.ProcessarEventoAutorizacaoUseCase;
import br.com.srportto.autorizacaostatusproducer.shared.config.AwsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Duration;

/**
 * Consome a fila SQS-eventos-autorizacao em loop de long polling numa virtual thread.
 * Semantica at-least-once: o ack (DeleteMessage) so acontece apos o processamento da
 * mensagem terminar sem excecao; em erro, a mensagem nao e confirmada e volta a fila
 * apos o visibility timeout. Falhas de ReceiveMessage (ex.: Floci fora do ar) aplicam
 * backoff sem encerrar o loop.
 */
@Component
public class SqsEventoAutorizacaoListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SqsEventoAutorizacaoListener.class);

    private static final int WAIT_TIME_SECONDS = 20;
    private static final int MAX_NUMBER_OF_MESSAGES = 10;
    private static final Duration BACKOFF_APOS_ERRO = Duration.ofSeconds(5);

    private final SqsClient sqsClient;
    private final AwsProperties awsProperties;
    private final ProcessarEventoAutorizacaoUseCase useCase;

    private volatile boolean running = false;
    private Thread pollingThread;

    public SqsEventoAutorizacaoListener(SqsClient sqsClient, AwsProperties awsProperties,
            ProcessarEventoAutorizacaoUseCase useCase) {
        this.sqsClient = sqsClient;
        this.awsProperties = awsProperties;
        this.useCase = useCase;
    }

    @Override
    public void start() {
        running = true;
        pollingThread = Thread.ofVirtual().name("sqs-eventos-autorizacao-listener").start(this::loopDeConsumo);
    }

    @Override
    public void stop() {
        running = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loopDeConsumo() {
        String queueUrl = awsProperties.sqs().queueUrl();
        log.info("Iniciando consumo da fila {}", queueUrl);

        while (running) {
            pollOnce();
        }

        log.info("Consumo da fila {} encerrado", queueUrl);
    }

    void pollOnce() {
        String queueUrl = awsProperties.sqs().queueUrl();

        try {
            var response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .waitTimeSeconds(WAIT_TIME_SECONDS)
                    .maxNumberOfMessages(MAX_NUMBER_OF_MESSAGES)
                    .build());

            for (Message message : response.messages()) {
                processarEDarAck(queueUrl, message);
            }
        } catch (Exception e) {
            log.error("Falha ao consumir a fila {}. Nova tentativa em {}", queueUrl, BACKOFF_APOS_ERRO, e);
            aguardarBackoff();
        }
    }

    void processarEDarAck(String queueUrl, Message message) {
        try {
            useCase.processar(message.body());

            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
        } catch (Exception e) {
            log.error("Falha ao processar a mensagem {}. Não será confirmada — volta à fila após o visibility timeout",
                    message.messageId(), e);
        }
    }

    private void aguardarBackoff() {
        try {
            Thread.sleep(BACKOFF_APOS_ERRO);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

}
