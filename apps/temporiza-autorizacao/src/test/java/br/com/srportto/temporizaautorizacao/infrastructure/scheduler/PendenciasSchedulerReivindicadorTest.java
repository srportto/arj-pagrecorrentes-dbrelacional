package br.com.srportto.temporizaautorizacao.infrastructure.scheduler;

import br.com.srportto.temporizaautorizacao.infrastructure.config.TemporizacaoProperties;
import br.com.srportto.temporizaautorizacao.infrastructure.messaging.ExpiracaoStreamListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
@DisplayName("Testes do PendenciasSchedulerReivindicador")
class PendenciasSchedulerReivindicadorTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private StreamOperations<String, Object, Object> streamOperations;
    @Mock
    private ExpiracaoStreamListener listener;

    private final TemporizacaoProperties properties = new TemporizacaoProperties(
            10, 5000, 100, 120000, "agenda:{pixauto:j1}", "stream:{pixauto:j1}:expiracoes",
            "temporizaautorizacao", "worker-1", "http://localhost:8080", 5000, 600000);

    @Test
    @DisplayName("reivindica e reprocessa apenas as pendências ociosas além do limiar")
    void reivindicaApenasOciosas() {
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);

        var ociosa = new PendingMessage(RecordId.of("1-0"), Consumer.from("g", "c"),
                Duration.ofMillis(999999), 1);
        var recente = new PendingMessage(RecordId.of("2-0"), Consumer.from("g", "c"),
                Duration.ofMillis(10), 1);
        var pendentes = new PendingMessages(properties.grupoConsumidor(), List.of(ociosa, recente));

        when(streamOperations.pending(eq(properties.chaveStream()), eq(properties.grupoConsumidor()),
                any(Range.class), anyLong())).thenReturn(pendentes);

        MapRecord<String, Object, Object> reivindicada = StreamRecords.<String, Object, Object>newRecord()
                .in(properties.chaveStream())
                .withId(RecordId.of("1-0"))
                .ofMap(Map.of("id_autorizacao", "abc-123"));

        when(streamOperations.claim(eq(properties.chaveStream()), eq(properties.grupoConsumidor()),
                eq(properties.consumidorId()), any(XClaimOptions.class)))
                .thenReturn(List.of(reivindicada));

        var scheduler = new PendenciasSchedulerReivindicador(redisTemplate, listener, properties);
        scheduler.reivindicarPendenciasOciosas();

        verify(listener).processarEConfirmarSeConcluido(RecordId.of("1-0"), "abc-123");
        verify(listener, never()).processarEConfirmarSeConcluido(eq(RecordId.of("2-0")), any());
    }

    @Test
    @DisplayName("sem pendências, não reivindica nada")
    void semPendenciasNaoReivindica() {
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
        when(streamOperations.pending(eq(properties.chaveStream()), eq(properties.grupoConsumidor()),
                any(Range.class), anyLong()))
                .thenReturn(new PendingMessages(properties.grupoConsumidor(), List.of()));

        var scheduler = new PendenciasSchedulerReivindicador(redisTemplate, listener, properties);
        scheduler.reivindicarPendenciasOciosas();

        verify(listener, never()).processarEConfirmarSeConcluido(any(), any());
    }

    @Test
    @DisplayName("entrada que esgota o teto de tentativas é confirmada sem novo acionamento do command")
    void entradaEsgotadaNaoReprocessaEApenasConfirma() {
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);

        var esgotada = new PendingMessage(RecordId.of("3-0"), Consumer.from("g", "c"),
                Duration.ofMillis(999999), 5);
        var pendentes = new PendingMessages(properties.grupoConsumidor(), List.of(esgotada));

        when(streamOperations.pending(eq(properties.chaveStream()), eq(properties.grupoConsumidor()),
                any(Range.class), anyLong())).thenReturn(pendentes);

        MapRecord<String, Object, Object> reivindicada = StreamRecords.<String, Object, Object>newRecord()
                .in(properties.chaveStream())
                .withId(RecordId.of("3-0"))
                .ofMap(Map.of("id_autorizacao", "esgotada-456"));

        when(streamOperations.claim(eq(properties.chaveStream()), eq(properties.grupoConsumidor()),
                eq(properties.consumidorId()), any(XClaimOptions.class)))
                .thenReturn(List.of(reivindicada));

        var scheduler = new PendenciasSchedulerReivindicador(redisTemplate, listener, properties);
        scheduler.reivindicarPendenciasOciosas();

        verify(streamOperations).acknowledge(properties.chaveStream(), properties.grupoConsumidor(),
                RecordId.of("3-0"));
        verify(listener, never()).processarEConfirmarSeConcluido(any(), any());
    }

    @Test
    @DisplayName("entrada abaixo do teto de tentativas continua sendo reivindicada e reprocessada")
    void entradaAbaixoDoTetoContinuaReprocessando() {
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);

        var quaseNoTeto = new PendingMessage(RecordId.of("4-0"), Consumer.from("g", "c"),
                Duration.ofMillis(999999), 4);
        var pendentes = new PendingMessages(properties.grupoConsumidor(), List.of(quaseNoTeto));

        when(streamOperations.pending(eq(properties.chaveStream()), eq(properties.grupoConsumidor()),
                any(Range.class), anyLong())).thenReturn(pendentes);

        MapRecord<String, Object, Object> reivindicada = StreamRecords.<String, Object, Object>newRecord()
                .in(properties.chaveStream())
                .withId(RecordId.of("4-0"))
                .ofMap(Map.of("id_autorizacao", "quase-789"));

        when(streamOperations.claim(eq(properties.chaveStream()), eq(properties.grupoConsumidor()),
                eq(properties.consumidorId()), any(XClaimOptions.class)))
                .thenReturn(List.of(reivindicada));

        var scheduler = new PendenciasSchedulerReivindicador(redisTemplate, listener, properties);
        scheduler.reivindicarPendenciasOciosas();

        verify(listener).processarEConfirmarSeConcluido(RecordId.of("4-0"), "quase-789");
        verify(streamOperations, never()).acknowledge(any(), any(), any(RecordId.class));
    }

    @Test
    @DisplayName("grupo/stream ainda não existe: exceção é engolida, sem reivindicar")
    void grupoInexistenteNaoLanca() {
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
        when(streamOperations.pending(eq(properties.chaveStream()), eq(properties.grupoConsumidor()),
                any(Range.class), anyLong()))
                .thenThrow(new RuntimeException("NOGROUP"));

        var scheduler = new PendenciasSchedulerReivindicador(redisTemplate, listener, properties);
        scheduler.reivindicarPendenciasOciosas();

        verify(listener, never()).processarEConfirmarSeConcluido(any(), any());
    }

}
