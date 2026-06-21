package br.com.srportto.contratocommand.domain.services.cancelamento;

import br.com.srportto.contratocommand.shared.validationsetup.Rule;

public interface CancelamentoRule extends Rule<CancelamentoContext> {

    @Override
    default String getLogCode() {
        return "CancelamentoRule: Validando regra de negocio para cancelar autorizacao";
    }

}
