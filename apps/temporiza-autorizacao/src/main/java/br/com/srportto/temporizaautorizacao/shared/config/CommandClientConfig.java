package br.com.srportto.temporizaautorizacao.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Cliente HTTP síncrono usado para acionar {@code PATCH /api/autorizacoes/{id}/decisao} no arj-contratocommand. */
@Configuration
@EnableConfigurationProperties(TemporizacaoProperties.class)
public class CommandClientConfig {

    @Bean
    public RestClient commandRestClient(TemporizacaoProperties properties) {
        var timeout = Duration.ofMillis(properties.commandTimeoutMs());

        var jdkHttpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();

        var requestFactory = new JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(properties.commandBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

}
