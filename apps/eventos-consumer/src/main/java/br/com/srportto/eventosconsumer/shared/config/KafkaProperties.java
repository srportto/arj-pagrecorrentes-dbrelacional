package br.com.srportto.eventosconsumer.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka")
public record KafkaProperties(String bootstrapServers, String schemaRegistryUrl, String topic, String groupId,
        boolean autoRegisterSchemas) {
}
