package br.com.srportto.contratocommand.domain.service.decisao;

import br.com.srportto.contratocommand.domain.port.in.DecidirAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.service.Rule;

public interface DecisaoRule extends Rule<DecidirAutorizacaoCommand> {

    @Override
    default String getLogCode() {
        return "DecisaoRule: Validando regra de negocio para decisao sobre autorizacao";
    }

}
