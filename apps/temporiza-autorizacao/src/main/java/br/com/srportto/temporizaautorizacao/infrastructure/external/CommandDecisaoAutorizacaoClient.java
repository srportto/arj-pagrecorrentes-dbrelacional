package br.com.srportto.temporizaautorizacao.infrastructure.external;

import br.com.srportto.temporizaautorizacao.domain.exception.ExpiracaoRetryavelException;
import br.com.srportto.temporizaautorizacao.domain.port.out.DecisaoAutorizacaoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/** Adapter de saída: PATCH síncrono em {@code /api/autorizacoes/{id}/decisao} com {@code acao=EXPIRAR}. */
@Component
public class CommandDecisaoAutorizacaoClient implements DecisaoAutorizacaoClient {

    private static final Logger log = LoggerFactory.getLogger(CommandDecisaoAutorizacaoClient.class);

    private final RestClient restClient;

    public CommandDecisaoAutorizacaoClient(RestClient commandRestClient) {
        this.restClient = commandRestClient;
    }

    @Override
    public void expirar(UUID idAutorizacao) {
        try {
            restClient.patch()
                    .uri("/api/autorizacoes/{id}/decisao", idAutorizacao)
                    .header("tipoProduto", "PIX_AUTO")
                    .body(Map.of("acao", "EXPIRAR"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            // 409: transação revertida no command, diferente dos demais 4xx — exige retry.
            throw new ExpiracaoRetryavelException(
                    "Conflito de concorrência (409) ao expirar " + idAutorizacao + " — tentar novamente", e);
        } catch (HttpClientErrorException e) {
            // Demais 4xx (incl. 422 "já não é RECEBIDA"): trabalho concluído, nada a fazer.
            log.info("Command respondeu {} para expiração de {} — nada a fazer (autorização já resolvida "
                    + "ou não encontrada)", e.getStatusCode(), idAutorizacao);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new ExpiracaoRetryavelException(
                    "Falha ao acionar o command para expirar " + idAutorizacao, e);
        }
    }

}
