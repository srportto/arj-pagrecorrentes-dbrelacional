package br.com.srportto.temporizaautorizacao.application.expiracao;

import org.springframework.stereotype.Service;

import java.util.UUID;

/** Orquestra o trabalho de uma entrada do stream: aciona o command. Não decide ack — isso é do worker. */
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
