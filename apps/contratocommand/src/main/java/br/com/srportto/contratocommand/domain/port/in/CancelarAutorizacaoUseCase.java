package br.com.srportto.contratocommand.domain.port.in;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;

public interface CancelarAutorizacaoUseCase {

    Autorizacao execute(CancelarAutorizacaoCommand command);

}
