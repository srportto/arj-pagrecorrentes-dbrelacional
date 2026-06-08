package br.com.srportto.contratocommand.application.defaultservice.cancelamento;

import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.shared.validationsetup.Rule;

public interface CancelamentoRule extends Rule<CancelarAutorizacaoRequestDto> {

    @Override
    default String getLogCode() {
        return "CancelamentoRule: Validando regra de negocio para cancelar autorizacao";
    }

}
