package br.com.srportto.contratocommand.application.defaultservice.cancelamento;

import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;

public interface CancelamentoService {

   boolean validaCancelamentoSuportado(CancelarAutorizacaoRequestDto request);

   AutorizacaoCompletaResponseDto cancelarAutorizacao(CancelarAutorizacaoRequestDto request);

}
