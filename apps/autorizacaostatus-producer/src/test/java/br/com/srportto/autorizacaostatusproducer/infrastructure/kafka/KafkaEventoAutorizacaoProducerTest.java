package br.com.srportto.autorizacaostatusproducer.infrastructure.kafka;

import br.com.srportto.autorizacaostatusproducer.shared.config.KafkaProperties;
import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

}
