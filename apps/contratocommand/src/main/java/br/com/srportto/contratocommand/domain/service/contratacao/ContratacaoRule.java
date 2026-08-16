package br.com.srportto.contratocommand.domain.service.contratacao;

import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.service.Rule;

public interface ContratacaoRule extends Rule<CriarAutorizacaoCommand> {

    @Override
    default String getLogCode() {
        return "ContratacaoRule: Validando regra de negocio para criacao de autorizacao";
    }

}
