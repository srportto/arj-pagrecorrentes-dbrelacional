package br.com.srportto.temporizaautorizacao.domain.port.in;

import java.util.UUID;

/** Porta de entrada: orquestra o trabalho de uma entrada do stream, acionando o command. */
public interface ProcessarExpiracaoUseCase {

    void processar(UUID idAutorizacao);

}
