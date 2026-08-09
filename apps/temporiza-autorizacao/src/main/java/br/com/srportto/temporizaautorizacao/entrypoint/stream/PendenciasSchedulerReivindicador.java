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

/**
 * Reivindica (XCLAIM) entradas do stream que ficaram pendentes além de
 * {@code stream-min-idle-time-ms} — instância morta entre a leitura e o ACK, ou processamento
 * travado. As reivindicadas são processadas pelo mesmo caminho do listener normal.
 */
@Component
public class PendenciasSchedulerReivindicador {

    private static final Logger log = LoggerFactory.getLogger(PendenciasSchedulerReivindicador.class);
    private static final long LOTE_PENDENTES = 100;

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
            // grupo/stream ainda nao existe (nenhuma expiracao disparou ate agora) — nada a reivindicar.
            return;
        }

        if (pendentes == null || pendentes.isEmpty()) {
            return;
        }

        var minIdle = Duration.ofMillis(properties.streamMinIdleTimeMs());
        List<String> idsOciosos = pendentes.stream()
                .filter(msg -> msg.getElapsedTimeSinceLastDelivery().compareTo(minIdle) >= 0)
                .map(PendingMessage::getIdAsString)
                .toList();

        if (idsOciosos.isEmpty()) {
            return;
        }

        var xClaimOptions = XClaimOptions.minIdle(minIdle).ids(idsOciosos);
        List<MapRecord<String, Object, Object>> reivindicadas = streamOps.claim(
                properties.chaveStream(), properties.grupoConsumidor(), properties.consumidorId(), xClaimOptions);

        log.info("Reivindicadas {} pendência(s) ociosa(s) no stream '{}'",
                reivindicadas.size(), properties.chaveStream());

        for (MapRecord<String, Object, Object> record : reivindicadas) {
            String idAutorizacao = String.valueOf(record.getValue().get("id_autorizacao"));
            listener.processarEConfirmarSeConcluido(record.getId(), idAutorizacao);
        }
    }

}
