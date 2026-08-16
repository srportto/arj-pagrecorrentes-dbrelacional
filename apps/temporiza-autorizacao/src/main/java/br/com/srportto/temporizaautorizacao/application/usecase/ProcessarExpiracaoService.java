package br.com.srportto.temporizaautorizacao.application.usecase;

import br.com.srportto.temporizaautorizacao.domain.port.in.ProcessarExpiracaoUseCase;
import br.com.srportto.temporizaautorizacao.domain.port.out.DecisaoAutorizacaoClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Orquestra o trabalho de uma entrada do stream: aciona o command. Não decide ack — isso é do worker. */
@Service
public class ProcessarExpiracaoService implements ProcessarExpiracaoUseCase {

    private final DecisaoAutorizacaoClient decisaoAutorizacaoClient;

    public ProcessarExpiracaoService(DecisaoAutorizacaoClient decisaoAutorizacaoClient) {
        this.decisaoAutorizacaoClient = decisaoAutorizacaoClient;
    }

    @Override
    public void processar(String idAutorizacaoStr) {
        var idAutorizacao = UUID.fromString(idAutorizacaoStr);
        decisaoAutorizacaoClient.expirar(idAutorizacao);
    }

}
