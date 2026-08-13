package br.com.srportto.temporizaautorizacao.entrypoint.stream;

import br.com.srportto.temporizaautorizacao.shared.config.TemporizacaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Rede de segurança p/ SIGKILL/OOM (@PreDestroy não roda) — remove consumidor ocioso e sem pendência. */
@Component
public class ConsumidoresOrfaosLimpezaScheduler {

    private static final Logger log = LoggerFactory.getLogger(ConsumidoresOrfaosLimpezaScheduler.class);

    private final StringRedisTemplate redisTemplate;
    private final ConsumidorRemocaoService remocaoService;
    private final TemporizacaoProperties properties;

    public ConsumidoresOrfaosLimpezaScheduler(StringRedisTemplate redisTemplate,
            ConsumidorRemocaoService remocaoService, TemporizacaoProperties properties) {
        this.redisTemplate = redisTemplate;
        this.remocaoService = remocaoService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${temporizacao.stream-min-idle-time-ms}")
    public void removerConsumidoresOrfaos() {
        StreamInfo.XInfoConsumers consumidores;
        try {
            consumidores = redisTemplate.opsForStream().consumers(properties.chaveStream(), properties.grupoConsumidor());
        } catch (RuntimeException e) {
            // grupo/stream ainda nao existe — nada a limpar.
            return;
        }

        for (var consumidor : consumidores) {
            if (consumidor.idleTimeMs() < properties.consumidorOciosoLimiteMs()) {
                continue;
            }

            boolean removido = remocaoService.removerSeSemPendencia(consumidor.consumerName(), consumidor.pendingCount());
            if (removido) {
                log.info("Consumidor órfão '{}' removido do grupo '{}' — ocioso há {} ms",
                        consumidor.consumerName(), properties.grupoConsumidor(), consumidor.idleTimeMs());
            }
        }
    }

}
