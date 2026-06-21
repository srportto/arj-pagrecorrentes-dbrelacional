package br.com.srportto.contratocommand.application.defaultservice.cancelamento;

import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;

public interface CancelamentoService {

   boolean validaCancelamentoSuportado(CancelamentoContext context);

   AutorizacaoCompletaResponseDto cancelarAutorizacao(CancelamentoContext context);

}
