package br.com.srportto.contratocommand.application.defaultservice.contratacao;

import org.springframework.stereotype.Component;

import java.util.List;

import br.com.srportto.contratocommand.shared.validationsetup.Validator;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class ContratacaoValidator implements Validator<ContratacaoRule, CriarAutorizacaoRequest> {

    private final List<ContratacaoRule> contratacaoRules;

    @Override
    public String getLogCode() {
        return "ContratacaoValidator: Validando regras de negocio para criacao de autorizacao";
    }

    public List<ContratacaoRule> getRules() {
        return contratacaoRules;
    }
}
