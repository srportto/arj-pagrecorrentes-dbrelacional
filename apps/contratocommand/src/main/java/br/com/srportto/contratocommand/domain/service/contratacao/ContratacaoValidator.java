package br.com.srportto.contratocommand.domain.service.contratacao;

import br.com.srportto.contratocommand.application.contratacao.ContratacaoContext;
import br.com.srportto.contratocommand.domain.service.Validator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ContratacaoValidator implements Validator<ContratacaoRule, ContratacaoContext> {

    private final List<ContratacaoRule> contratacaoRules;

    @Override
    public String getLogCode() {
        return "ContratacaoValidator: Validando regras de negocio para criacao de autorizacao";
    }

    public List<ContratacaoRule> getRules() {
        return contratacaoRules;
    }
}
