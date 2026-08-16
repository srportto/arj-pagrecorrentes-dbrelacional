package br.com.srportto.temporizaautorizacao.domain.port.in;

/** Porta de entrada: orquestra o trabalho de uma entrada do stream, acionando o command. */
public interface ProcessarExpiracaoUseCase {

    void processar(String idAutorizacaoStr);

}
