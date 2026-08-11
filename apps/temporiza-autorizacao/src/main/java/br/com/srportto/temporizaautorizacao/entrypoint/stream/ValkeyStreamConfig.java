package br.com.srportto.temporizaautorizacao.entrypoint.stream;

import br.com.srportto.temporizaautorizacao.shared.config.TemporizacaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
 */
@Configuration
@EnableConfigurationProperties(TemporizacaoProperties.class)
public class ValkeyStreamConfig {

    private static final Logger log = LoggerFactory.getLogger(ValkeyStreamConfig.class);

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

    // TODO: consumidor nunca é removido do grupo (7 órfãos p/ 2 pods em 2026-08-09) — ver change limpar-consumidores-orfaos-stream
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

}
