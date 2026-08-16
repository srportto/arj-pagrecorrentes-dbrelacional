package br.com.srportto.contratocommand.domain.service.decisao;

import br.com.srportto.contratocommand.domain.port.in.DecidirAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.service.Validator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class DecisaoValidator implements Validator<DecisaoRule, DecidirAutorizacaoCommand> {

    private final List<DecisaoRule> decisaoRules;

    @Override
    public String getLogCode() {
        return "DecisaoValidator: Validando regras de negocio para decisao sobre autorizacao";
    }

    public List<DecisaoRule> getRules() {
        return decisaoRules;
    }
}
