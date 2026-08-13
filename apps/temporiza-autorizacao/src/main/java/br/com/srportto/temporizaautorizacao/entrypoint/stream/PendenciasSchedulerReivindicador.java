package br.com.srportto.temporizaautorizacao.entrypoint.stream;

import br.com.srportto.temporizaautorizacao.shared.config.TemporizacaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** Reivindica (XCLAIM) entradas pendentes além de {@code stream-min-idle-time-ms}, reprocessando pelo caminho normal. */
@Component
public class PendenciasSchedulerReivindicador {

    private static final Logger log = LoggerFactory.getLogger(PendenciasSchedulerReivindicador.class);
    private static final long LOTE_PENDENTES = 100;
    private static final long MAX_TENTATIVAS_EXPIRACAO = 5;

    private final StringRedisTemplate redisTemplate;
    private final ExpiracaoStreamListener listener;
    private final TemporizacaoProperties properties;

    public PendenciasSchedulerReivindicador(StringRedisTemplate redisTemplate, ExpiracaoStreamListener listener,
            TemporizacaoProperties properties) {
        this.redisTemplate = redisTemplate;
        this.listener = listener;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${temporizacao.stream-min-idle-time-ms}")
    public void reivindicarPendenciasOciosas() {
        StreamOperations<String, Object, Object> streamOps = redisTemplate.opsForStream();

        PendingMessages pendentes;
        try {
            pendentes = streamOps.pending(properties.chaveStream(), properties.grupoConsumidor(),
                    Range.unbounded(), LOTE_PENDENTES);
        } catch (RuntimeException e) {
            // grupo/stream ainda nao existe — nada a reivindicar.
            return;
        }

        if (pendentes == null || pendentes.isEmpty()) {
            return;
        }

        var minIdle = Duration.ofMillis(properties.streamMinIdleTimeMs());
        List<PendingMessage> ociosas = pendentes.stream()
                .filter(msg -> msg.getElapsedTimeSinceLastDelivery().compareTo(minIdle) >= 0)
                .toList();

        if (ociosas.isEmpty()) {
            return;
        }

        List<PendingMessage> esgotadas = ociosas.stream()
                .filter(msg -> msg.getTotalDeliveryCount() >= MAX_TENTATIVAS_EXPIRACAO)
                .toList();
        List<String> idsReprocessaveis = ociosas.stream()
                .filter(msg -> msg.getTotalDeliveryCount() < MAX_TENTATIVAS_EXPIRACAO)
                .map(PendingMessage::getIdAsString)
                .toList();

        if (!esgotadas.isEmpty()) {
            desistirDeEntradasEsgotadas(streamOps, minIdle, esgotadas);
        }

        if (idsReprocessaveis.isEmpty()) {
            return;
        }

        var xClaimOptions = XClaimOptions.minIdle(minIdle).ids(idsReprocessaveis);
        List<MapRecord<String, Object, Object>> reivindicadas = streamOps.claim(
                properties.chaveStream(), properties.grupoConsumidor(), properties.consumidorId(), xClaimOptions);

        log.info("Reivindicadas {} pendência(s) ociosa(s) no stream '{}'",
                reivindicadas.size(), properties.chaveStream());

        for (MapRecord<String, Object, Object> record : reivindicadas) {
            String idAutorizacao = String.valueOf(record.getValue().get("id_autorizacao"));
            listener.processarEConfirmarSeConcluido(record.getId(), idAutorizacao);
        }
    }

    /** Entradas esgotadas param de recircular: confirmadas direto (sem novo acionamento) e logadas p/ investigação manual. */
    private void desistirDeEntradasEsgotadas(StreamOperations<String, Object, Object> streamOps,
            Duration minIdle, List<PendingMessage> esgotadas) {
        var ids = esgotadas.stream().map(PendingMessage::getIdAsString).toList();
        var xClaimOptions = XClaimOptions.minIdle(minIdle).ids(ids);
        List<MapRecord<String, Object, Object>> reivindicadas = streamOps.claim(
                properties.chaveStream(), properties.grupoConsumidor(), properties.consumidorId(), xClaimOptions);

        for (MapRecord<String, Object, Object> record : reivindicadas) {
            String idAutorizacao = String.valueOf(record.getValue().get("id_autorizacao"));
            log.error("Entrada {} (autorização {}) esgotou o teto de {} tentativas — confirmando sem novo "
                            + "acionamento do command; requer investigação manual",
                    record.getId(), idAutorizacao, MAX_TENTATIVAS_EXPIRACAO);
            streamOps.acknowledge(properties.chaveStream(), properties.grupoConsumidor(), record.getId());
        }
    }

}
