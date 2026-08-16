package br.com.srportto.contratocommand.domain.port.in;

import br.com.srportto.contratocommand.domain.model.Autorizacao;

public interface DecidirAutorizacaoUseCase {

    Autorizacao execute(DecidirAutorizacaoCommand command);

}
