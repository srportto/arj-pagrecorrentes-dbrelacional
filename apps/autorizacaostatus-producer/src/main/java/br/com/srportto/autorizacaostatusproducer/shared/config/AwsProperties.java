package br.com.srportto.autorizacaostatusproducer.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws")
public record AwsProperties(String endpoint, String region, String accessKey, String secretKey, Sqs sqs) {

    public record Sqs(String queueUrl) {
    }

}
