package br.com.srportto.temporizaautorizacao.infrastructure.scheduler;

import br.com.srportto.temporizaautorizacao.infrastructure.config.TemporizacaoProperties;
import br.com.srportto.temporizaautorizacao.infrastructure.messaging.ConsumidorStreamRemovedor;
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
    private final ConsumidorStreamRemovedor remocaoService;
    private final TemporizacaoProperties properties;

    public ConsumidoresOrfaosLimpezaScheduler(StringRedisTemplate redisTemplate,
            ConsumidorStreamRemovedor remocaoService, TemporizacaoProperties properties) {
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
            if (!ehGrupoOuStreamInexistente(e)) {
                log.warn("Falha inesperada ao consultar consumidores do stream '{}' — tentativa adiada para "
                        + "o próximo ciclo", properties.chaveStream(), e);
            }
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

    /** NOGROUP é a resposta do Valkey quando o grupo/stream ainda não existe — esperado no primeiro ciclo. */
    private boolean ehGrupoOuStreamInexistente(Throwable t) {
        for (Throwable atual = t; atual != null; atual = atual.getCause()) {
            if (atual.getMessage() != null && atual.getMessage().contains("NOGROUP")) {
                return true;
            }
        }
        return false;
    }

}
