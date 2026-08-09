package br.com.srportto.contratocommand.application.decisao;

import br.com.srportto.contratocommand.shared.validationsetup.Rule;

public interface DecisaoRule extends Rule<DecisaoContext> {

    @Override
    default String getLogCode() {
        return "DecisaoRule: Validando regra de negocio para decisao sobre autorizacao";
    }

}
