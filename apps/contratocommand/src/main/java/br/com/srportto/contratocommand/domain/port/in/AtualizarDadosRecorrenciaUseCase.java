package br.com.srportto.contratocommand.domain.port.in;

import br.com.srportto.contratocommand.domain.model.Autorizacao;

public interface AtualizarDadosRecorrenciaUseCase {

    Autorizacao execute(AtualizarDadosRecorrenciaCommand command);

}
