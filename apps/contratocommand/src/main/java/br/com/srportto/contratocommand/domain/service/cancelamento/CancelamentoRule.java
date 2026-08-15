package br.com.srportto.contratocommand.domain.service.cancelamento;

import br.com.srportto.contratocommand.application.cancelamento.CancelamentoContext;
import br.com.srportto.contratocommand.domain.service.Rule;

public interface CancelamentoRule extends Rule<CancelamentoContext> {

    @Override
    default String getLogCode() {
        return "CancelamentoRule: Validando regra de negocio para cancelar autorizacao";
    }

}
