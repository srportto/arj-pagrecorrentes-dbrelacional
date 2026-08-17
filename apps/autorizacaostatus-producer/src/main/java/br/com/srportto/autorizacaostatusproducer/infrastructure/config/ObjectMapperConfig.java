package br.com.srportto.autorizacaostatusproducer.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Único {@link ObjectMapper} da app — desserialização do payload SQS em {@code infrastructure/messaging}. */
@Configuration
public class ObjectMapperConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
