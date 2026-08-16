package br.com.srportto.temporizaautorizacao.infrastructure.config;

import br.com.srportto.temporizaautorizacao.infrastructure.messaging.ConsumidorRemocaoService;
import br.com.srportto.temporizaautorizacao.infrastructure.messaging.ExpiracaoStreamListener;
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
 * Cria o consumer group do stream de expirações (idempotente) e registra a subscription com ACK
 * MANUAL — só confirma via {@link ExpiracaoStreamListener#processarEConfirmarSeConcluido}, nunca
 * automaticamente. No encerramento gracioso remove o consumidor desta instância (camada 1 da
 * higiene de órfãos; camada 2 é
 * {@link br.com.srportto.temporizaautorizacao.infrastructure.scheduler.ConsumidoresOrfaosLimpezaScheduler}).
 *
 * <p>Usa {@link SmartLifecycle} (não {@code @PreDestroy}): o {@code LettuceConnectionFactory} para
 * sua conexão antes da fase de destruição de beans, e {@code @PreDestroy} rodaria tarde demais
 * (confirmado empiricamente). Fase maior garante que {@link #stop()} rode antes, com conexão viva.
 */
@Configuration
@EnableConfigurationProperties(TemporizacaoProperties.class)
public class ValkeyStreamConfig implements SmartLifecycle {

    /** Maior que a fase padrão (0) do LettuceConnectionFactory: garante conexão viva em {@link #stop()}. */
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

    /** Camada 1 da higiene de órfãos. Falha aqui é higiene, não negócio — nunca propaga nem atrasa o shutdown. */
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
