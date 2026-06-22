package br.com.srportto.contratocommand.domain.services.contratacao;

import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;

public interface ContratacaoService {

   boolean validaContratacaoSuportada(CriarAutorizacaoRequest request);

   AutorizacaoCompletaResponseDto criarAutorizacao(CriarAutorizacaoRequest request);

}
