package br.com.srportto.contratocommand.application.contratacao;

import br.com.srportto.contratocommand.shared.validationsetup.Rule;

public interface ContratacaoRule extends Rule<ContratacaoContext> {

    @Override
    default String getLogCode() {
        return "ContratacaoRule: Validando regra de negocio para criacao de autorizacao";
    }

}
