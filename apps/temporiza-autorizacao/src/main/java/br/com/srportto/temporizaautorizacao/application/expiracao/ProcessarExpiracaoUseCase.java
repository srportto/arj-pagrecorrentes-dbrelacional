package br.com.srportto.temporizaautorizacao.application.expiracao;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orquestra o trabalho de uma entrada do stream de expirações: aciona o command. Não decide
 * ack — quem lê o stream (worker) confirma somente se este método retornar sem exceção.
 */
@Service
public class ProcessarExpiracaoUseCase {

    private final DecisaoAutorizacaoClient decisaoAutorizacaoClient;

    public ProcessarExpiracaoUseCase(DecisaoAutorizacaoClient decisaoAutorizacaoClient) {
        this.decisaoAutorizacaoClient = decisaoAutorizacaoClient;
    }

    public void processar(String idAutorizacaoStr) {
        var idAutorizacao = UUID.fromString(idAutorizacaoStr);
        decisaoAutorizacaoClient.expirar(idAutorizacao);
    }

}
