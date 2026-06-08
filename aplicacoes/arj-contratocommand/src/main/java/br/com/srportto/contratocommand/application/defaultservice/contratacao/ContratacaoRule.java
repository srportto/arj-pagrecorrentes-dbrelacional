package br.com.srportto.contratocommand.application.defaultservice.contratacao;

import br.com.srportto.contratocommand.shared.validationsetup.Rule;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;

public interface ContratacaoRule extends Rule<CriarAutorizacaoRequest> {

    @Override
    default String getLogCode() {
        return "ContratacaoRule: Validando regra de negocio para criacao de autorizacao";
    }

}
