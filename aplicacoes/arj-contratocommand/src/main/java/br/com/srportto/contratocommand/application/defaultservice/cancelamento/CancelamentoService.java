package br.com.srportto.contratocommand.application.defaultservice.cancelamento;

import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;

public interface CancelamentoService {

   boolean validaCancelamentoSuportado(CancelarAutorizacaoRequestDto request);


   default AutorizacaoCompletaResponseDto cancelarAutorizacao(CancelarAutorizacaoRequestDto request) {
      throw new UnsupportedOperationException("Método cancelarAutorizacao não implementado");
   }

}
