package br.com.srportto.contratocommand.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

import java.net.URI;

/**
 * Cliente SNS via AWS SDK v2 puro. No profile local aponta para o Floci (endpoint +
 * credenciais estaticas); em prod usa a cadeia padrao de credenciais (ex.: task role do
 * ECS) e o endpoint real da AWS.
 */
@Configuration
@EnableConfigurationProperties(AwsProperties.class)
public class SnsClientConfig {

    @Bean
    public SnsClient snsClient(AwsProperties awsProperties) {
        var builder = SnsClient.builder()
                .region(Region.of(awsProperties.region()));

        if (StringUtils.hasText(awsProperties.endpoint())) {
            builder.endpointOverride(URI.create(awsProperties.endpoint()));
        }

        if (StringUtils.hasText(awsProperties.accessKey()) && StringUtils.hasText(awsProperties.secretKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(awsProperties.accessKey(), awsProperties.secretKey())));
        }

        return builder.build();
    }

}
