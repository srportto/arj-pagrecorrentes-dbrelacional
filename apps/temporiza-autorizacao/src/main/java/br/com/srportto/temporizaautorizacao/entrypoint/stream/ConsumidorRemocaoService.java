package br.com.srportto.temporizaautorizacao.entrypoint.stream;

import br.com.srportto.temporizaautorizacao.shared.config.TemporizacaoProperties;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Remoção segura de um consumidor do consumer group do stream de expirações — compartilhada
 * entre o encerramento gracioso ({@link ValkeyStreamConfig}) e a varredura periódica por
 * ociosidade ({@link ConsumidoresOrfaosLimpezaScheduler}). "Segura" significa: nunca remove
 * consumidor com PEL não vazio — {@code XGROUP DELCONSUMER} descarta as entradas pendentes do
 * consumidor removido, e elas não voltam ao grupo nem são reivindicáveis depois.
 */
@Component
public class ConsumidorRemocaoService {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorRemocaoService.class);

    private final StringRedisTemplate redisTemplate;
    private final TemporizacaoProperties properties;

    public ConsumidorRemocaoService(StringRedisTemplate redisTemplate, TemporizacaoProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * Remove {@code consumidorId} do grupo se {@code pendingCount} for zero. O chamador SHALL ler
     * {@code pendingCount} imediatamente antes desta chamada — nunca reaproveitar leitura de um
     * ciclo anterior.
     *
     * @return {@code true} se o consumidor foi removido; {@code false} se foi mantido por ter pendência.
     */
    public boolean removerSeSemPendencia(String consumidorId, long pendingCount) {
        if (pendingCount > 0) {
            log.info("Consumidor '{}' mantido no grupo '{}': {} entrada(s) pendente(s)",
                    consumidorId, properties.grupoConsumidor(), pendingCount);
            return false;
        }

        Long descartadas = removerConsumidor(consumidorId);
        if (descartadas != null && descartadas > 0) {
            log.error("Remoção do consumidor '{}' do grupo '{}' descartou {} entrada(s) pendente(s) — "
                            + "a verificação prévia de pending foi violada",
                    consumidorId, properties.grupoConsumidor(), descartadas);
        }
        return true;
    }

    /**
     * {@code XGROUP DELCONSUMER} via API nativa do driver Lettuce: a API tipada do Spring Data
     * Redis ({@code StreamOperations#deleteConsumer}) devolve apenas {@code Boolean} de sucesso,
     * e {@code RedisConnection#execute(String, byte[]...)} genérico decodifica a resposta como
     * bulk string (não decodifica o inteiro que o comando real devolve — falha em runtime com
     * {@code UnsupportedOperationException}, confirmado empiricamente). O comando real devolve a
     * contagem de entradas pendentes descartadas, que é exatamente o que
     * {@link #removerSeSemPendencia} precisa conferir — só a API nativa expõe esse valor tipado.
     */
    private Long removerConsumidor(String consumidorId) {
        return redisTemplate.execute((RedisCallback<Long>) connection -> {
            var nativeConnection = connection.getNativeConnection();
            if (!(nativeConnection instanceof RedisClusterAsyncCommands<?, ?> asyncCommands)) {
                throw new IllegalStateException(
                        "Conexão nativa inesperada (driver diferente de Lettuce): " + nativeConnection.getClass());
            }
            @SuppressWarnings("unchecked")
            var comandos = (RedisClusterAsyncCommands<byte[], byte[]>) asyncCommands;
            var consumidorLettuce = io.lettuce.core.Consumer.<byte[]>from(
                    properties.grupoConsumidor().getBytes(StandardCharsets.UTF_8),
                    consumidorId.getBytes(StandardCharsets.UTF_8));
            RedisFuture<Long> future = comandos.xgroupDelconsumer(
                    properties.chaveStream().getBytes(StandardCharsets.UTF_8), consumidorLettuce);
            try {
                return future.get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrompido aguardando XGROUP DELCONSUMER", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException("Falha ao aguardar XGROUP DELCONSUMER", e);
            }
        });
    }

}
