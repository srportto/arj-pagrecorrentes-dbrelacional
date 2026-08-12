package br.com.srportto.temporizaautorizacao.entrypoint.stream;

import br.com.srportto.temporizaautorizacao.shared.config.TemporizacaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rede de segurança para consumidores de instâncias que morreram sem encerramento gracioso
 * (SIGKILL, OOM, nó perdido) — cenário em que o {@code @PreDestroy} de {@link ValkeyStreamConfig}
 * não tem chance de rodar. Remove periodicamente quem está ocioso além do limiar configurado e
 * sem pendência.
 *
 * <p>Uma instância viva não se aproxima do limiar: o container do listener faz polling bloqueante
 * contínuo, que reseta o {@code idle} do consumidor a cada {@code XREADGROUP} — mesmo quando não
 * há dado novo. Só um consumidor cujo processo parou de existir acumula {@code idle} de verdade.
 */
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
            // grupo/stream ainda nao existe (nenhuma expiracao disparou ate agora) — nada a limpar.
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
