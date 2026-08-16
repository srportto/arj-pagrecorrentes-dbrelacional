package br.com.srportto.contratocommand.domain.service.cancelamento;

import br.com.srportto.contratocommand.domain.port.in.CancelarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.service.Rule;

public interface CancelamentoRule extends Rule<CancelarAutorizacaoCommand> {

    @Override
    default String getLogCode() {
        return "CancelamentoRule: Validando regra de negocio para cancelar autorizacao";
    }

}
