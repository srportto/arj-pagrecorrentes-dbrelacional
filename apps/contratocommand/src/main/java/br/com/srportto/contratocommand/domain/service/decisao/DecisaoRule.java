package br.com.srportto.contratocommand.domain.service.decisao;

import br.com.srportto.contratocommand.application.decisao.DecisaoContext;
import br.com.srportto.contratocommand.domain.service.Rule;

public interface DecisaoRule extends Rule<DecisaoContext> {

    @Override
    default String getLogCode() {
        return "DecisaoRule: Validando regra de negocio para decisao sobre autorizacao";
    }

}
