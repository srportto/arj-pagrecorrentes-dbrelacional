package br.com.srportto.autorizacaostatusproducer.application.eventos;

import br.com.srportto.autorizacaostatusproducer.shared.exceptions.EventoAutorizacaoInvalidoException;
import br.com.srportto.autorizacaostatusproducer.shared.exceptions.EventoAutorizacaoKafkaIndisponivelException;
import br.com.srportto.autorizacaostatusproducer.shared.config.KafkaProperties;
import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.avro.AvroRuntimeException;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do KafkaEventoAutorizacaoProducer")
class KafkaEventoAutorizacaoProducerTest {

    @Mock
    private Producer<String, EventoAutorizacao> producer;

    private final KafkaProperties kafkaProperties =
            new KafkaProperties("localhost:19092", "http://localhost:8085", "eventos-autorizacao");

    private KafkaEventoAutorizacaoProducer kafkaEventoAutorizacaoProducer;

    private void inicializar() {
        kafkaEventoAutorizacaoProducer = new KafkaEventoAutorizacaoProducer(producer, kafkaProperties);
    }

    private EventoAutorizacao eventoMinimo() {
        return EventoAutorizacao.newBuilder()
                .setIdAutorizacao(UUID.randomUUID())
                .setIdParticaoConta(950)
                .setDataFimVigencia(LocalDate.now())
                .setTipoProduto(1L)
                .setStatus(4)
                .setDataHoraInclusao(LocalDateTime.now())
                .setDataHoraUltimaAtlz(LocalDateTime.now())
                .setCodigoCanalContratacao("canal")
                .build();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<ProducerRecord<String, EventoAutorizacao>> captor() {
        return ArgumentCaptor.forClass(ProducerRecord.class);
    }

    @Test
    @DisplayName("produz no tópico configurado com a key e o header tipoEvento")
    void produzComSucesso() {
        inicializar();
        when(producer.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        EventoAutorizacao evento = eventoMinimo();

        kafkaEventoAutorizacaoProducer.produzir("key-1", evento, "CRIACAO");

        ArgumentCaptor<ProducerRecord<String, EventoAutorizacao>> captor = captor();
        verify(producer).send(captor.capture());
        ProducerRecord<String, EventoAutorizacao> record = captor.getValue();
        assertEquals("eventos-autorizacao", record.topic());
        assertEquals("key-1", record.key());
        assertEquals(evento, record.value());
        assertEquals("CRIACAO",
                new String(record.headers().lastHeader("tipoEvento").value(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("falha do broker (ExecutionException) vira EventoAutorizacaoKafkaIndisponivelException")
    void falhaDoBrokerViraExcecaoRetryable() {
        inicializar();
        CompletableFuture<RecordMetadata> futureFalho = new CompletableFuture<>();
        futureFalho.completeExceptionally(new RuntimeException("kafka fora do ar"));
        when(producer.send(any())).thenReturn(futureFalho);

        assertThrows(EventoAutorizacaoKafkaIndisponivelException.class,
                () -> kafkaEventoAutorizacaoProducer.produzir("key-1", eventoMinimo(), "CANCELAMENTO"));
    }

    @Test
    @DisplayName("SerializationException por incompatibilidade de schema é não-retryable (payload inválido)")
    void serializationPorSchemaIncompativelEhNaoRetryable() {
        inicializar();
        // como o KafkaAvroSerializer faz: reembala a falha de conversão Avro
        when(producer.send(any())).thenThrow(new SerializationException(
                "Error serializing Avro message", new AvroRuntimeException("campo x incompatível")));

        assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> kafkaEventoAutorizacaoProducer.produzir("key-1", eventoMinimo(), "ATIVACAO"));
    }

    @Test
    @DisplayName("SerializationException causada por IOException do Schema Registry é RETRYABLE — não descarta mensagem válida")
    void serializationPorRegistryForaDoArEhRetryable() {
        inicializar();
        // AbstractKafkaAvroSerializer reembala a IOException da chamada HTTP ao Registry
        // na MESMA SerializationException usada para payload inválido
        when(producer.send(any())).thenThrow(new SerializationException(
                "Error serializing Avro message", new IOException("Connection refused: localhost:8085")));

        assertThrows(EventoAutorizacaoKafkaIndisponivelException.class,
                () -> kafkaEventoAutorizacaoProducer.produzir("key-1", eventoMinimo(), "ATIVACAO"));
    }

    @Test
    @DisplayName("IOException aninhada em qualquer nível da cadeia é reconhecida como indisponibilidade")
    void ioExceptionAninhadaProfundamenteEhRetryable() {
        inicializar();
        when(producer.send(any())).thenThrow(new SerializationException("Error serializing Avro message",
                new RuntimeException("wrapper", new IOException("timeout no Schema Registry"))));

        assertThrows(EventoAutorizacaoKafkaIndisponivelException.class,
                () -> kafkaEventoAutorizacaoProducer.produzir("key-1", eventoMinimo(), "ATIVACAO"));
    }

    @Test
    @DisplayName("Registry respondendo HTTP 500 (RestClientException, sem IOException) é RETRYABLE")
    void restClientExceptionDoRegistryEhRetryable() {
        inicializar();
        // toKafkaException do AbstractKafkaSchemaSerDe: status fora de 408/429/502/503/504
        // vira SerializationException com cause RestClientException — que NÃO é IOException
        when(producer.send(any())).thenThrow(new SerializationException(
                "Error retrieving Avro schema",
                new RestClientException("Internal Server Error", 500, 50001)));

        assertThrows(EventoAutorizacaoKafkaIndisponivelException.class,
                () -> kafkaEventoAutorizacaoProducer.produzir("key-1", eventoMinimo(), "ATIVACAO"));
    }

    @Test
    @DisplayName("TimeoutException do Registry (HTTP 503) é RETRYABLE — nem chega como SerializationException")
    void timeoutDoRegistryEhRetryable() {
        inicializar();
        when(producer.send(any())).thenThrow(new org.apache.kafka.common.errors.TimeoutException(
                "Error retrieving Avro schema", new RestClientException("Service Unavailable", 503, 50301)));

        assertThrows(EventoAutorizacaoKafkaIndisponivelException.class,
                () -> kafkaEventoAutorizacaoProducer.produzir("key-1", eventoMinimo(), "ATIVACAO"));
    }

    @Test
    @DisplayName("SocketTimeoutException do cliente HTTP do Registry (http.connect/read.timeout.ms) é RETRYABLE")
    void socketTimeoutDoClienteHttpDoRegistryEhRetryable() {
        inicializar();
        // SocketTimeoutException e IOException: e o que http.connect.timeout.ms e
        // http.read.timeout.ms (SchemaRegistryClientConfig) produzem quando o Registry
        // nao responde dentro do teto configurado em KafkaProducerClientConfig
        when(producer.send(any())).thenThrow(new SerializationException(
                "Error retrieving Avro schema",
                new java.net.SocketTimeoutException("Read timed out")));

        assertThrows(EventoAutorizacaoKafkaIndisponivelException.class,
                () -> kafkaEventoAutorizacaoProducer.produzir("key-1", eventoMinimo(), "ATIVACAO"));
    }

    @Test
    @DisplayName("KafkaException genérica sem causa de dado é RETRYABLE — default seguro, não descarta")
    void kafkaExceptionDesconhecidaEhRetryable() {
        inicializar();
        when(producer.send(any())).thenThrow(new KafkaException("falha não catalogada"));

        assertThrows(EventoAutorizacaoKafkaIndisponivelException.class,
                () -> kafkaEventoAutorizacaoProducer.produzir("key-1", eventoMinimo(), "ATIVACAO"));
    }

    @Test
    @DisplayName("cadeia de causas cíclica não trava a classificação")
    void cadeiaCiclicaNaoTrava() {
        inicializar();
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        when(producer.send(any())).thenThrow(new SerializationException("ciclo", b));

        assertThrows(EventoAutorizacaoKafkaIndisponivelException.class,
                () -> kafkaEventoAutorizacaoProducer.produzir("key-1", eventoMinimo(), "ATIVACAO"));
    }

    @Test
    @DisplayName("mensagem da falha de schema não carrega o cause (que poderia embutir o valor do campo)")
    void mensagemDeSchemaInvalidoNaoCarregaCause() {
        inicializar();
        String pii = "CPF-12345678900";
        when(producer.send(any())).thenThrow(new SerializationException(
                "Error serializing Avro message", new AvroRuntimeException("valor rejeitado: " + pii)));

        var e = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> kafkaEventoAutorizacaoProducer.produzir("key-1", eventoMinimo(), "ATIVACAO"));

        assertNull(e.getCause());
        assertFalse(e.getMessage().contains(pii));
    }

}
