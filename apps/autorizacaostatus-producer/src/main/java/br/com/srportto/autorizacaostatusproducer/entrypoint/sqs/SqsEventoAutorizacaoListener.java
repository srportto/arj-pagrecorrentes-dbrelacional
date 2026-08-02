package br.com.srportto.autorizacaostatusproducer.entrypoint.sqs;

import br.com.srportto.autorizacaostatusproducer.shared.exceptions.EventoAutorizacaoInvalidoException;
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
 * Consome a fila em loop de long polling numa virtual thread e produz cada evento no Kafka
 * (ponte); ack só após a confirmação do Kafka.
 *
 * <p>Nenhum log daqui carrega o body da mensagem: o payload contém dado pessoal. A mensagem
 * é identificada pelo {@code messageId} do SQS.
 */
@Component
public class SqsEventoAutorizacaoListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SqsEventoAutorizacaoListener.class);

    private static final int WAIT_TIME_SECONDS = 20;
    private static final int MAX_NUMBER_OF_MESSAGES = 10;
    private static final Duration BACKOFF_APOS_ERRO = Duration.ofSeconds(5);
    /**
     * Acima do pior caso de uma mensagem (20s) e estritamente ABAIXO do
     * {@code spring.lifecycle.timeout-per-shutdown-phase} (40s, declarado no
     * application.yaml). Sem essa margem o Spring poderia expirar a fase de shutdown e
     * começar a destruir os beans — fechando SqsClient/Producer — enquanto esta thread
     * ainda estivesse dentro do join, anulando a garantia que o join existe para dar.
     */
    private static final Duration TIMEOUT_ENCERRAMENTO = Duration.ofSeconds(25);

    private final SqsClient sqsClient;
    private final AwsProperties awsProperties;
    private final ProcessarEventoAutorizacaoUseCase useCase;

    private volatile boolean running = false;
    // volatile: o health indicator le esta referencia de outra thread (a requisicao HTTP do
    // /actuator/health); sem isso nao ha garantia de visibilidade da atribuicao feita no start()
    private volatile Thread pollingThread;

    public SqsEventoAutorizacaoListener(SqsClient sqsClient, AwsProperties awsProperties,
            ProcessarEventoAutorizacaoUseCase useCase) {
        this.sqsClient = sqsClient;
        this.awsProperties = awsProperties;
        this.useCase = useCase;
    }

    @Override
    public void start() {
        // pollingThread ANTES de running: o health indicator so consulta a thread quando
        // running e true, entao a referencia precisa estar publicada antes da flag
        pollingThread = Thread.ofVirtual()
                .name("sqs-eventos-autorizacao-listener")
                .unstarted(this::loopDeConsumo);
        running = true;
        pollingThread.start();
    }

    /**
     * Sinaliza a parada, interrompe a thread de polling e aguarda seu término. Sem essa
     * espera o contexto Spring destruiria o SqsClient e o Producer Kafka com uma mensagem
     * ainda em voo — evento produzido no Kafka sem ack no SQS, ou seja, duplicata na
     * próxima subida.
     */
    @Override
    public void stop() {
        running = false;
        if (pollingThread == null) {
            return;
        }

        pollingThread.interrupt();
        try {
            pollingThread.join(TIMEOUT_ENCERRAMENTO.toMillis());
            if (pollingThread.isAlive()) {
                log.warn("Thread de polling não encerrou em {} — prosseguindo com o shutdown",
                        TIMEOUT_ENCERRAMENTO);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Espera pelo encerramento da thread de polling foi interrompida", e);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Liveness real da thread de polling, distinta da flag {@link #isRunning()} — que
     * continuaria {@code true} se a thread morresse. Consultado pelo health indicator.
     */
    public boolean isThreadDePollingViva() {
        return pollingThread != null && pollingThread.isAlive();
    }

    private void loopDeConsumo() {
        String queueUrl = awsProperties.sqs().queueUrl();
        log.info("Iniciando consumo da fila {}", queueUrl);

        while (running) {
            try {
                pollOnce();
            } catch (Throwable t) {
                // rede de seguranca contra Error: sem isto a thread morreria em silencio e
                // a aplicacao continuaria reportando saude UP com o consumidor parado
                log.error("Falha inesperada no ciclo de consumo da fila {}. Nova tentativa em {}",
                        queueUrl, BACKOFF_APOS_ERRO, t);
                aguardarBackoff();
            }
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
            ack(queueUrl, message);
        } catch (EventoAutorizacaoInvalidoException e) {
            log.error("Mensagem não-retryable descartada: messageId={}", message.messageId(), e);
            ack(queueUrl, message);
        } catch (Exception e) {
            log.error("Falha ao processar a mensagem messageId={}. Não será confirmada — volta à fila "
                    + "após o visibility timeout", message.messageId(), e);
        }
    }

    private void ack(String queueUrl, Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
    }

    private void aguardarBackoff() {
        try {
            Thread.sleep(BACKOFF_APOS_ERRO);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

}
