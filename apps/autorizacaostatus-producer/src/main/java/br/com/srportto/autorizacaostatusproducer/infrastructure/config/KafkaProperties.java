package br.com.srportto.autorizacaostatusproducer.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka")
public record KafkaProperties(String bootstrapServers, String schemaRegistryUrl, String topic,
        boolean autoRegisterSchemas) {
}
