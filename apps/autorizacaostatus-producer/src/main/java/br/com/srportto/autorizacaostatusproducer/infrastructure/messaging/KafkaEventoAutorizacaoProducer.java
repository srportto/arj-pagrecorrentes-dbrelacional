package br.com.srportto.autorizacaostatusproducer.infrastructure.messaging;

import br.com.srportto.autorizacaostatusproducer.domain.exception.EventoAutorizacaoInvalidoException;
import br.com.srportto.autorizacaostatusproducer.domain.exception.EventoAutorizacaoKafkaIndisponivelException;
import br.com.srportto.autorizacaostatusproducer.domain.port.out.PublicadorEventoAutorizacao;
import br.com.srportto.autorizacaostatusproducer.infrastructure.config.KafkaProperties;
import org.apache.avro.AvroRuntimeException;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Produz eventos no tópico Kafka de forma síncrona; falha vira {@link EventoAutorizacaoKafkaIndisponivelException} (retryable). */
@Component
public class KafkaEventoAutorizacaoProducer implements PublicadorEventoAutorizacao {

    private static final String HEADER_TIPO_EVENTO = "tipoEvento";
    private static final long GET_TIMEOUT_SECONDS = 20;

    private final Producer<String, br.com.srportto.eventos.autorizacao.EventoAutorizacao> producer;
    private final KafkaProperties kafkaProperties;
    private final EventoAutorizacaoAvroMapper avroMapper;

    public KafkaEventoAutorizacaoProducer(Producer<String, br.com.srportto.eventos.autorizacao.EventoAutorizacao> producer,
            KafkaProperties kafkaProperties, EventoAutorizacaoAvroMapper avroMapper) {
        this.producer = producer;
        this.kafkaProperties = kafkaProperties;
        this.avroMapper = avroMapper;
    }

    @Override
    public void produzir(String key, br.com.srportto.autorizacaostatusproducer.domain.model.EventoAutorizacao evento,
            String tipoEvento) {
        br.com.srportto.eventos.autorizacao.EventoAutorizacao eventoAvro = avroMapper.paraAvro(evento);
        ProducerRecord<String, br.com.srportto.eventos.autorizacao.EventoAutorizacao> record =
                new ProducerRecord<>(kafkaProperties.topic(), null, key, eventoAvro);
        record.headers().add(HEADER_TIPO_EVENTO, tipoEvento.getBytes(StandardCharsets.UTF_8));

        try {
            producer.send(record).get(GET_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (KafkaException e) {
            throw classificarFalhaDoProduce(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventoAutorizacaoKafkaIndisponivelException("Interrompido ao produzir evento no Kafka", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new EventoAutorizacaoKafkaIndisponivelException("Falha ao produzir evento no Kafka", e);
        }
    }

    /**
     * Classifica a falha síncrona de {@code send()} (serialização Avro + round-trip ao Schema
     * Registry, antes do Future). Default é <strong>retryable</strong>: o Registry despacha
     * indisponibilidade em vários tipos de exceção distintos, difíceis de enumerar sem risco de
     * esquecer um caso e perder a mensagem. Só é não-retryable o que for comprovadamente dado
     * incompatível com o schema (Avro/ClassCastException); todo o resto volta à fila.
     */
    private RuntimeException classificarFalhaDoProduce(KafkaException e) {
        if (e instanceof SerializationException && causaEhDadoIncompativel(e)) {
            // sem o cause: a mensagem do serializer Avro pode embutir o valor do campo rejeitado
            return new EventoAutorizacaoInvalidoException(
                    "Evento incompatível com o schema Avro registrado (" + causaRaizDe(e) + ")");
        }

        return new EventoAutorizacaoKafkaIndisponivelException(
                "Falha ao serializar ou produzir o evento no Kafka", e);
    }

    private boolean causaEhDadoIncompativel(Throwable falha) {
        for (Throwable atual : cadeiaDeCausas(falha)) {
            if (atual instanceof AvroRuntimeException || atual instanceof ClassCastException) {
                return true;
            }
        }
        return false;
    }

    private String causaRaizDe(Throwable falha) {
        String nome = falha.getClass().getSimpleName();
        for (Throwable atual : cadeiaDeCausas(falha)) {
            nome = atual.getClass().getSimpleName();
        }
        return nome;
    }

    /** Percorre a cadeia protegendo contra ciclo — o JDK só impede {@code cause == this}. */
    private List<Throwable> cadeiaDeCausas(Throwable falha) {
        Set<Throwable> visitados = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Throwable> cadeia = new ArrayList<>();
        for (Throwable atual = falha; atual != null && visitados.add(atual); atual = atual.getCause()) {
            cadeia.add(atual);
        }
        return cadeia;
    }

}
