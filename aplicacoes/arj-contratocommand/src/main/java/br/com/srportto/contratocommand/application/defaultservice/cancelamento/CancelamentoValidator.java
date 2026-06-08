package br.com.srportto.contratocommand.application.defaultservice.cancelamento;

import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.shared.validationsetup.Validator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
public class CancelamentoValidator implements Validator<CancelamentoRule, CancelarAutorizacaoRequestDto> {

    private final List<CancelamentoRule> cancelamentoRules;

    @Override
    public String getLogCode() {
        return "CancelamentoValidator: Validando regras de negocio para cancelar de autorizacao";
    }

    public List<CancelamentoRule> getRules() {
        return cancelamentoRules;
    }
}
