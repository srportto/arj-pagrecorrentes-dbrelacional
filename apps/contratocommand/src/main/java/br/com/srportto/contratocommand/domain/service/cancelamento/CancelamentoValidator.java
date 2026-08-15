package br.com.srportto.contratocommand.domain.service.cancelamento;

import br.com.srportto.contratocommand.domain.port.in.CancelarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.service.Validator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CancelamentoValidator implements Validator<CancelamentoRule, CancelarAutorizacaoCommand> {

    private final List<CancelamentoRule> cancelamentoRules;

    @Override
    public String getLogCode() {
        return "CancelamentoValidator: Validando regras de negocio para cancelar de autorizacao";
    }

    public List<CancelamentoRule> getRules() {
        return cancelamentoRules;
    }
}
