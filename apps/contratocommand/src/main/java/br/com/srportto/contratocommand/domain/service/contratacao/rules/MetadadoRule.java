package br.com.srportto.contratocommand.domain.service.contratacao.rules;

import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.service.contratacao.ContratacaoRule;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class MetadadoRule implements ContratacaoRule {

    private static final int MAX_LENGTH = 255;

    // Comando carrega metadados como texto JSON (D1: comando não importa DTO de request); a
    // rule reparseia aqui para inspecionar campos, sem levar Jackson para o comando em si.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public boolean aceita(CriarAutorizacaoCommand contexto) {
        return true;
    }

    @Override
    public void validar(CriarAutorizacaoCommand contexto) {
        if (contexto.metadados() == null) {
            return;
        }

        JsonNode metadados = OBJECT_MAPPER.readTree(contexto.metadados());

        validarNomePessoaRecebedora(metadados);
        validarApelidoPessoaRecebedora(metadados);
    }

    private void validarNomePessoaRecebedora(JsonNode metadados) {
        if (metadados.has("nomePessoaRecebedora")) {
            String nome = metadados.get("nomePessoaRecebedora").asString();
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
            String apelido = metadados.get("apelidoPessoaRecebedora").asString();
            if (apelido != null && apelido.length() > MAX_LENGTH) {
                throw new BusinessException(
                        String.format(
                                "O campo 'apelidoPessoaRecebedora' no metadado não pode exceder %d caracteres. Comprimento atual: %d",
                                MAX_LENGTH, apelido.length()));
            }
        }
    }
}
