package br.com.srportto.temporizaautorizacao.infrastructure.scheduler;

import br.com.srportto.temporizaautorizacao.infrastructure.config.TemporizacaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Poda (XTRIM MINID) as entradas do stream de expirações já confirmadas — sem isso o stream
 * cresce indefinidamente, porque XACK só remove do PEL, nunca do stream em si. O limite de poda
 * é sempre o menor id ainda pendente no grupo de consumo (XPENDING); se o PEL estiver vazio, é o
 * último id entregue ao grupo (XINFO GROUPS). Uma entrada ainda pendente nunca é removida.
 *
 * <p>Sem esta poda, o crescimento é perigoso de verdade: o ElastiCache Valkey provisionado não
 * define {@code parameter_group_name}, então herda a política default {@code volatile-lru} — como
 * nenhuma chave desta app tem TTL, nenhuma é elegível a eviction, e ao atingir {@code maxmemory}
 * toda escrita (inclusive o agendamento via {@code ZADD}) passa a falhar, deixando autorizações
 * PIX_AUTO presas em RECEBIDA para sempre.
 */
@Component
public class ExpiracaoStreamTrimScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiracaoStreamTrimScheduler.class);

    private final StringRedisTemplate redisTemplate;
    private final TemporizacaoProperties properties;

    public ExpiracaoStreamTrimScheduler(StringRedisTemplate redisTemplate, TemporizacaoProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${temporizacao.stream-min-idle-time-ms}")
    public void podarEntradasConfirmadas() {
        String minId;
        try {
            minId = calcularLimiteDePoda();
        } catch (RuntimeException e) {
            if (ehGrupoOuStreamInexistente(e)) {
                return;
            }
            log.warn("Falha inesperada ao consultar pendências do stream '{}' para poda — tentativa "
                    + "adiada para o próximo ciclo", properties.chaveStream(), e);
            return;
        }

        if (minId == null) {
            return;
        }

        Long removidas = xTrimMinId(properties.chaveStream(), minId);
        if (removidas != null && removidas > 0) {
            log.info("Poda do stream '{}': {} entrada(s) confirmada(s) removida(s), retido a partir de {}",
                    properties.chaveStream(), removidas, minId);
        }
    }

    /** Menor id ainda pendente no grupo; com o PEL vazio, o último id já entregue (tudo antes disso foi processado). */
    private String calcularLimiteDePoda() {
        PendingMessagesSummary pendentes = redisTemplate.opsForStream()
                .pending(properties.chaveStream(), properties.grupoConsumidor());

        if (pendentes != null && pendentes.getTotalPendingMessages() > 0) {
            return pendentes.minMessageId();
        }
        return ultimoIdEntregue();
    }

    /**
     * PEL vazio: nenhuma entrega em aberto, logo até o último id entregue já foi processado e
     * confirmado. {@code MINID} é inclusivo — usar o próprio {@code lastDeliveredId} deixaria essa
     * entrada para trás; o sucessor imediato do id (mesmo timestamp, sequência seguinte) é o
     * limite correto para removê-la também.
     */
    private String ultimoIdEntregue() {
        StreamInfo.XInfoGroups grupos = redisTemplate.opsForStream().groups(properties.chaveStream());
        return grupos.stream()
                .filter(g -> properties.grupoConsumidor().equals(g.groupName()))
                .map(StreamInfo.XInfoGroup::lastDeliveredId)
                .filter(id -> !"0-0".equals(id))
                .map(this::sucessorDoId)
                .findFirst()
                .orElse(null);
    }

    /** Id de stream é "&lt;millis&gt;-&lt;sequencia&gt;" — o sucessor incrementa a sequência em 1. */
    private String sucessorDoId(String id) {
        int separador = id.lastIndexOf('-');
        String millis = id.substring(0, separador);
        long sequencia = Long.parseLong(id.substring(separador + 1));
        return millis + "-" + (sequencia + 1);
    }

    /** Spring Data Redis não expõe XTRIM MINID (só MAXLEN) — comando emitido em baixo nível. */
    private Long xTrimMinId(String chave, String minId) {
        return redisTemplate.execute((RedisCallback<Long>) connection -> {
            Object resultado = connection.execute("XTRIM",
                    chave.getBytes(StandardCharsets.UTF_8),
                    "MINID".getBytes(StandardCharsets.UTF_8),
                    minId.getBytes(StandardCharsets.UTF_8));
            return resultado == null ? null : (Long) resultado;
        });
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
