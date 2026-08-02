package br.com.srportto.eventosconsumer.shared.config;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumer Kafka via spring-kafka (quebra deliberada da jurisprudencia "cliente puro"
 * do listener SQS): menos codigo, error handling e retry prontos via
 * DefaultErrorHandler. AckMode.RECORD definido em ContainerProperties (nao via
 * spring.kafka.listener.ack-mode — ver design.md D1 da mudanca refactor-eventos-consumer,
 * o configurer do Boot so aceita factory <Object, Object>): o offset so avanca apos o
 * listener retornar sem excecao, sem Acknowledgment manual. Mensagem que esgota as
 * tentativas (falha de negocio ou de desserializacao) vai para eventos-autorizacao.DLT
 * via DeadLetterPublishingRecoverer.
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, EventoAutorizacao> eventoAutorizacaoConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.groupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, kafkaProperties.schemaRegistryUrl());
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventoAutorizacao> eventoAutorizacaoKafkaListenerContainerFactory(
            ConsumerFactory<String, EventoAutorizacao> eventoAutorizacaoConsumerFactory,
            DefaultErrorHandler eventoAutorizacaoErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, EventoAutorizacao> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(eventoAutorizacaoConsumerFactory);
        // offset avanca automaticamente apos o listener retornar sem excecao, sem Acknowledgment manual
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(eventoAutorizacaoErrorHandler);
        return factory;
    }

    /** 3 tentativas com 1s de intervalo; esgotadas, publica na DLT (não-ack indefinido). */
    @Bean
    public DefaultErrorHandler eventoAutorizacaoErrorHandler(
            DeadLetterPublishingRecoverer eventoAutorizacaoDeadLetterRecoverer) {
        return new DefaultErrorHandler(eventoAutorizacaoDeadLetterRecoverer, new FixedBackOff(1_000L, 3));
    }

    /**
     * Falha de desserialização (bytes crus, via ErrorHandlingDeserializer) e falha de
     * negócio após desserialização com sucesso (EventoAutorizacao típado) são publicadas
     * na DLT por templates diferentes, roteados pelo tipo do valor do record.
     */
    @Bean
    public DeadLetterPublishingRecoverer eventoAutorizacaoDeadLetterRecoverer(
            KafkaTemplate<String, byte[]> eventoAutorizacaoDltBytesKafkaTemplate,
            KafkaTemplate<String, EventoAutorizacao> eventoAutorizacaoDltAvroKafkaTemplate) {
        return new DeadLetterPublishingRecoverer(Map.of(
                byte[].class, eventoAutorizacaoDltBytesKafkaTemplate,
                EventoAutorizacao.class, eventoAutorizacaoDltAvroKafkaTemplate));
    }

    @Bean
    public KafkaTemplate<String, byte[]> eventoAutorizacaoDltBytesKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        ProducerFactory<String, byte[]> producerFactory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public KafkaTemplate<String, EventoAutorizacao> eventoAutorizacaoDltAvroKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, kafkaProperties.schemaRegistryUrl());
        props.put(KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        ProducerFactory<String, EventoAutorizacao> producerFactory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(producerFactory);
    }

}
