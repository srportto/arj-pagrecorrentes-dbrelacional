package br.com.srportto.eventosconsumer.infrastructure.config;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.SerializationUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Publicação na DLT é roteada por tipo do valor do record: bytes crus (falha de
 * desserialização) pelo template de bytes; record já desserializado (falha de negócio) pelo Avro.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes da DLT (DeadLetterPublishingRecoverer) do KafkaConsumerConfig")
class KafkaConsumerConfigTest {

    private final KafkaConsumerConfig config = new KafkaConsumerConfig();

    @Mock
    private KafkaTemplate<String, byte[]> bytesTemplate;
    @Mock
    private KafkaTemplate<String, EventoAutorizacao> avroTemplate;

    private EventoAutorizacao eventoMinimo() {
        return EventoAutorizacao.newBuilder()
                .setIdAutorizacao(UUID.randomUUID())
                .setIdParticaoConta(950)
                .setDataFimVigencia(LocalDate.now())
                .setTipoProduto(1L)
                .setStatus(99)
                .setDataHoraInclusao(LocalDateTime.now())
                .setDataHoraUltimaAtlz(LocalDateTime.now())
                .setCodigoCanalContratacao("canal")
                .build();
    }

    @Test
    @DisplayName("status desconhecido (falha de negócio, record já desserializado) vai pelo template Avro")
    void falhaDeNegocioVaiPeloTemplateAvro() {
        when(avroTemplate.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        DeadLetterPublishingRecoverer recoverer =
                config.eventoAutorizacaoDeadLetterRecoverer(bytesTemplate, avroTemplate);
        EventoAutorizacao evento = eventoMinimo();
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "eventos-autorizacao", 0, 0L, "chave", evento);

        recoverer.accept(record, new IllegalArgumentException("status 99 não mapeado"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, EventoAutorizacao>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(avroTemplate).send(captor.capture());
        assertEquals("eventos-autorizacao.DLT", captor.getValue().topic(),
                "o destino real deve ser <topico>.DLT — o default do spring-kafka resolve para "
                        + "<topico>-dlt (hífen, minúsculo) e falharia contra um tópico inexistente");
    }

    @Test
    @DisplayName("falha de desserialização Avro é capturada pelo ErrorHandlingDeserializer e vai pelo template de bytes")
    void falhaDeDesserializacaoVaiPeloTemplateDeBytes() {
        when(bytesTemplate.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        KafkaAvroDeserializer delegate = new KafkaAvroDeserializer(new MockSchemaRegistryClient(),
                Map.of(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true,
                        KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://eventos-consumer-test"));
        ErrorHandlingDeserializer<Object> deserializer = new ErrorHandlingDeserializer<>(delegate);
        RecordHeaders headers = new RecordHeaders();
        byte[] bytesInvalidos = "payload-nao-e-avro-valido".getBytes(StandardCharsets.UTF_8);

        Object resultado = deserializer.deserialize("eventos-autorizacao", headers, bytesInvalidos);

        assertNull(resultado, "delegate falhou; ErrorHandlingDeserializer deve engolir a exceção e retornar null");
        assertNotNull(headers.lastHeader(SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER),
                "a exceção de desserialização deve ficar registrada no header para o recoverer extrair os bytes originais");

        DeadLetterPublishingRecoverer recoverer =
                config.eventoAutorizacaoDeadLetterRecoverer(bytesTemplate, avroTemplate);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "eventos-autorizacao", 0, 0L, 0L, org.apache.kafka.common.record.TimestampType.CREATE_TIME,
                0, 0, "chave", resultado, headers, java.util.Optional.empty());

        recoverer.accept(record, new org.apache.kafka.common.errors.SerializationException("falha de desserialização"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, byte[]>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(bytesTemplate).send(captor.capture());
        assertEquals("eventos-autorizacao.DLT", captor.getValue().topic(),
                "o destino real deve ser <topico>.DLT — o default do spring-kafka resolve para "
                        + "<topico>-dlt (hífen, minúsculo) e falharia contra um tópico inexistente");
    }

}
