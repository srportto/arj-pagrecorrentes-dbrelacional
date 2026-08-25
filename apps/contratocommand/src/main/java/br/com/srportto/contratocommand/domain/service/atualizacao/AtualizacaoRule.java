package br.com.srportto.contratocommand.domain.service.atualizacao;

import br.com.srportto.contratocommand.domain.port.in.AtualizarDadosRecorrenciaCommand;
import br.com.srportto.contratocommand.domain.service.Rule;

public interface AtualizacaoRule extends Rule<AtualizarDadosRecorrenciaCommand> {

    @Override
    default String getLogCode() {
        return "AtualizacaoRule: Validando regra de negocio para atualizacao de dados da recorrencia";
    }

}
