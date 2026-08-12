package br.com.srportto.temporizaautorizacao.entrypoint.stream;

import br.com.srportto.temporizaautorizacao.shared.config.TemporizacaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;

/**
 * Cria o consumer group do stream de expirações (idempotente — ignora "já existe") e registra a
 * subscription com ACK MANUAL: o container só confirma quando {@link ExpiracaoStreamListener}
 * chama {@code acknowledge} explicitamente, nunca automaticamente após o retorno do listener.
 *
 * <p>Também remove, no encerramento gracioso, o consumidor desta instância — camada 1 da higiene
 * de consumidores órfãos (camada 2 é a varredura periódica de
 * {@link ConsumidoresOrfaosLimpezaScheduler}). Ver {@code openspec/changes/limpar-consumidores-orfaos-stream}.
 *
 * <p><b>Por que {@link SmartLifecycle} e não {@code @PreDestroy}:</b> confirmado empiricamente
 * (verificação manual com 2 réplicas) que {@code @PreDestroy} roda tarde demais — o
 * {@code LettuceConnectionFactory} já implementa {@code SmartLifecycle} com fase padrão (0) e é
 * parado (fase de {@code Lifecycle.stop()}) **antes** da fase de destruição de beans
 * ({@code @PreDestroy}/{@code DisposableBean}) no fechamento do contexto Spring — a remoção
 * falhava com {@code IllegalStateException} porque a conexão já estava parada. Implementar
 * {@link SmartLifecycle} com fase maior que a do connection factory garante que {@link #stop()}
 * rode **antes**, com a conexão ainda viva.
 */
@Configuration
@EnableConfigurationProperties(TemporizacaoProperties.class)
public class ValkeyStreamConfig implements SmartLifecycle {

    /** Maior que a fase padrão (0) do LettuceConnectionFactory: para primeiro, conexão ainda viva. */
    private static final int FASE_PARADA_ANTES_DA_CONEXAO = 100;

    private volatile boolean running = false;

    private static final Logger log = LoggerFactory.getLogger(ValkeyStreamConfig.class);

    private final StringRedisTemplate redisTemplate;
    private final ConsumidorRemocaoService remocaoService;
    private final TemporizacaoProperties properties;

    public ValkeyStreamConfig(StringRedisTemplate redisTemplate, ConsumidorRemocaoService remocaoService,
            TemporizacaoProperties properties) {
        this.redisTemplate = redisTemplate;
        this.remocaoService = remocaoService;
        this.properties = properties;
    }

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofSeconds(2))
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);
        container.start();
        return container;
    }

    @Bean
    public Subscription expiracaoStreamSubscription(
            @Qualifier("streamMessageListenerContainer")
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
            StringRedisTemplate redisTemplate,
            ExpiracaoStreamListener listener,
            TemporizacaoProperties properties) {

        criarGrupoSeNaoExistir(redisTemplate, properties);

        var consumer = Consumer.from(properties.grupoConsumidor(), properties.consumidorId());
        var offset = StreamOffset.create(properties.chaveStream(), ReadOffset.lastConsumed());

        return container.receive(consumer, offset, listener);
    }

    /** MKSTREAM: o stream pode nem existir ainda (nenhuma expiração disparou até aqui). */
    private void criarGrupoSeNaoExistir(StringRedisTemplate redisTemplate, TemporizacaoProperties properties) {
        try {
            redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection ->
                    connection.streamCommands().xGroupCreate(
                            properties.chaveStream().getBytes(),
                            properties.grupoConsumidor(),
                            ReadOffset.from("0"),
                            true));
        } catch (DataAccessException e) {
            if (causaContemBusygroup(e)) {
                log.info("Consumer group '{}' já existe no stream '{}'",
                        properties.grupoConsumidor(), properties.chaveStream());
                return;
            }
            throw e;
        }
    }

    private boolean causaContemBusygroup(Throwable t) {
        for (Throwable atual = t; atual != null; atual = atual.getCause()) {
            if (atual.getMessage() != null && atual.getMessage().contains("BUSYGROUP")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void start() {
        running = true;
    }

    /**
     * Camada 1 da higiene de consumidores órfãos: remove o consumidor desta instância no
     * encerramento gracioso, se não tiver pendência. Falha aqui é higiene, não trabalho de
     * negócio — nunca propaga exceção nem atrasa o shutdown.
     */
    @Override
    public void stop() {
        try {
            var proprio = redisTemplate.opsForStream()
                    .consumers(properties.chaveStream(), properties.grupoConsumidor())
                    .stream()
                    .filter(c -> properties.consumidorId().equals(c.consumerName()))
                    .findFirst();

            proprio.ifPresent(c -> remocaoService.removerSeSemPendencia(c.consumerName(), c.pendingCount()));
        } catch (Exception e) {
            log.warn("Falha ao remover o consumidor '{}' do grupo '{}' no encerramento — "
                            + "higiene, não bloqueia o shutdown",
                    properties.consumidorId(), properties.grupoConsumidor(), e);
        } finally {
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return FASE_PARADA_ANTES_DA_CONEXAO;
    }

}
