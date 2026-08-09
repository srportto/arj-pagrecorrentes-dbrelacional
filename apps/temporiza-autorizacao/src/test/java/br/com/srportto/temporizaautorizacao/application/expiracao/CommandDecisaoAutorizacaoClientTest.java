package br.com.srportto.temporizaautorizacao.application.expiracao;

import br.com.srportto.temporizaautorizacao.shared.exceptions.ExpiracaoRetryavelException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Testes do CommandDecisaoAutorizacaoClient")
class CommandDecisaoAutorizacaoClientTest {

    @Test
    @DisplayName("2xx: expiração aplicada, retorna normalmente")
    void sucesso2xxNaoLancaExcecao() {
        var builder = RestClient.builder().baseUrl("http://command-fake");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new CommandDecisaoAutorizacaoClient(builder.build());
        var id = UUID.randomUUID();

        server.expect(requestTo("http://command-fake/api/autorizacoes/" + id + "/decisao"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> client.expirar(id));
        server.verify();
    }

    @Test
    @DisplayName("422 (status já não é RECEBIDA): nada a fazer, retorna normalmente")
    void status422NaoLancaExcecao() {
        var builder = RestClient.builder().baseUrl("http://command-fake");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new CommandDecisaoAutorizacaoClient(builder.build());
        var id = UUID.randomUUID();

        server.expect(requestTo("http://command-fake/api/autorizacoes/" + id + "/decisao"))
                .andRespond(withStatus(org.springframework.http.HttpStatusCode.valueOf(422))
                        .body("{\"message\":\"ja resolvida\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> client.expirar(id));
        server.verify();
    }

    @Test
    @DisplayName("5xx: falha retryável, mensagem permanece pendente")
    void status5xxLancaExcecaoRetryavel() {
        var builder = RestClient.builder().baseUrl("http://command-fake");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new CommandDecisaoAutorizacaoClient(builder.build());
        var id = UUID.randomUUID();

        server.expect(requestTo("http://command-fake/api/autorizacoes/" + id + "/decisao"))
                .andRespond(withServerError());

        assertThrows(ExpiracaoRetryavelException.class, () -> client.expirar(id));
        server.verify();
    }

}
