package br.com.srportto.contratocommand.application.defaultservice.contratacao.rules;

import org.springframework.stereotype.Component;

import br.com.srportto.contratocommand.application.defaultservice.contratacao.ContratacaoRule;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import tools.jackson.databind.JsonNode;

@Component
public class MetadadoRule implements ContratacaoRule {

    private static final int MAX_LENGTH = 255;

    @Override
    public boolean aceita(CriarAutorizacaoRequest request) {
        return true;
    }

    @Override
    public void validar(CriarAutorizacaoRequest request) {
        var metadados = request.metadados();

        if (metadados == null) {
            return;
        }

        validarNomePessoaRecebedora(metadados);
        validarApelidoPessoaRecebedora(metadados);
    }

    private void validarNomePessoaRecebedora(JsonNode metadados) {
        if (metadados.has("nomePessoaRecebedora")) {
            String nome = metadados.get("nomePessoaRecebedora").asText();
            if (nome != null && nome.length() > MAX_LENGTH) {
                throw new BusinessException(
                        String.format(
                                "O campo 'nomePessoaRecebedora' no metadado não pode exceder %d caracteres. Comprimento atual: %d",
                                MAX_LENGTH, nome.length()));
            }
        }
    }

    private void validarApelidoPessoaRecebedora(JsonNode metadados) {
        if (metadados.has("apelidoPessoaRecebedora")) {
            String apelido = metadados.get("apelidoPessoaRecebedora").asText();
            if (apelido != null && apelido.length() > MAX_LENGTH) {
                throw new BusinessException(
                        String.format(
                                "O campo 'apelidoPessoaRecebedora' no metadado não pode exceder %d caracteres. Comprimento atual: %d",
                                MAX_LENGTH, apelido.length()));
            }
        }
    }
}
