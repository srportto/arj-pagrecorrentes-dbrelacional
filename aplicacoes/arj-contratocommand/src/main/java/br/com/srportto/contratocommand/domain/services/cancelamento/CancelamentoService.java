package br.com.srportto.contratocommand.domain.services.cancelamento;

import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;

public interface CancelamentoService {

   boolean validaCancelamentoSuportado(CancelamentoContext context);

   AutorizacaoCompletaResponseDto cancelarAutorizacao(CancelamentoContext context);

}
