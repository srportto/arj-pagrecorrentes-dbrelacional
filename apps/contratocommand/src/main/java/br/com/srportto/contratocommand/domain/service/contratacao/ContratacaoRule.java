package br.com.srportto.contratocommand.domain.service.contratacao;

import br.com.srportto.contratocommand.application.contratacao.ContratacaoContext;
import br.com.srportto.contratocommand.domain.service.Rule;

public interface ContratacaoRule extends Rule<ContratacaoContext> {

    @Override
    default String getLogCode() {
        return "ContratacaoRule: Validando regra de negocio para criacao de autorizacao";
    }

}
