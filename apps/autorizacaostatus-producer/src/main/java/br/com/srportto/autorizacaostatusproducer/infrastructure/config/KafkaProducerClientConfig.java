package br.com.srportto.autorizacaostatusproducer.infrastructure.config;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;

/**
 * Producer Kafka via cliente puro (kafka-clients + KafkaAvroSerializer), sem spring-kafka.
 * Timeouts ficam abaixo do visibility timeout da fila SQS (60s) para a falha se resolver
 * antes de o SQS reentregar a mensagem.
 */
@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaProducerClientConfig {

    @Bean(destroyMethod = "close")
    public Producer<String, EventoAutorizacao> eventoAutorizacaoKafkaProducer(KafkaProperties kafkaProperties) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, kafkaProperties.schemaRegistryUrl());
        // true só no profile local; em prod exige registro explícito do schema antes do
        // primeiro produce (design.md de rede-seguranca-contrato-evento, decisão D5)
        props.put(KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, kafkaProperties.autoRegisterSchemas());

        // round-trip ao Schema Registry ocorre dentro de send(), antes do Future — nem
        // max.block.ms nem Future.get() cobrem esse caminho sem teto explícito aqui
        props.put(SchemaRegistryClientConfig.HTTP_CONNECT_TIMEOUT_MS, 3_000);
        props.put(SchemaRegistryClientConfig.HTTP_READ_TIMEOUT_MS, 3_000);

        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 15_000);

        return new KafkaProducer<>(props);
    }

}
