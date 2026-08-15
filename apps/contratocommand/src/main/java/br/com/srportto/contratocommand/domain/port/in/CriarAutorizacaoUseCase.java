package br.com.srportto.contratocommand.domain.port.in;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;

public interface CriarAutorizacaoUseCase {

    Autorizacao execute(CriarAutorizacaoCommand command);

}
