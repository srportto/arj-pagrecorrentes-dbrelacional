package br.com.srportto.temporizaautorizacao.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Testes do CommandClientConfig")
class CommandClientConfigTest {

    @Test
    @DisplayName("commandRestClient constrói um RestClient com o timeout configurado")
    void constroiRestClient() {
        var properties = new TemporizacaoProperties(
                10, 5000, 100, 120000, "agenda:{pixauto:j1}", "stream:{pixauto:j1}:expiracoes",
                "temporizaautorizacao", "worker-1", "http://localhost:8080", 5000, 600000);

        var config = new CommandClientConfig();

        assertNotNull(config.commandRestClient(properties));
    }

}
