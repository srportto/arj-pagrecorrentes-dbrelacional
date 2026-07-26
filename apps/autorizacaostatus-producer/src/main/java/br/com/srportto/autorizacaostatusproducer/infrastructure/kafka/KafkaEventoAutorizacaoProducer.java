package br.com.srportto.autorizacaostatusproducer.infrastructure.kafka;

import br.com.srportto.autorizacaostatusproducer.shared.config.KafkaProperties;
import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Produz eventos no tópico Kafka de forma síncrona; falha vira {@link EventoAutorizacaoKafkaIndisponivelException} (retryable). */
@Component
public class KafkaEventoAutorizacaoProducer {

    private static final String HEADER_TIPO_EVENTO = "tipoEvento";
    private static final long GET_TIMEOUT_SECONDS = 20;

    private final Producer<String, EventoAutorizacao> producer;
    private final KafkaProperties kafkaProperties;

    public KafkaEventoAutorizacaoProducer(Producer<String, EventoAutorizacao> producer,
            KafkaProperties kafkaProperties) {
        this.producer = producer;
        this.kafkaProperties = kafkaProperties;
    }

    public void produzir(String key, EventoAutorizacao evento, String tipoEvento) {
        ProducerRecord<String, EventoAutorizacao> record =
                new ProducerRecord<>(kafkaProperties.topic(), null, key, evento);
        record.headers().add(HEADER_TIPO_EVENTO, tipoEvento.getBytes(StandardCharsets.UTF_8));

        try {
            producer.send(record).get(GET_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventoAutorizacaoKafkaIndisponivelException("Interrompido ao produzir evento no Kafka", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new EventoAutorizacaoKafkaIndisponivelException("Falha ao produzir evento no Kafka", e);
        }
    }

}
