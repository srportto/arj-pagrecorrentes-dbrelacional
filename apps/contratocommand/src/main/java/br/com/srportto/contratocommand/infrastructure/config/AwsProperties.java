package br.com.srportto.contratocommand.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws")
public record AwsProperties(String endpoint, String region, String accessKey, String secretKey, Sns sns) {

    public record Sns(String topicArn) {
    }

}
