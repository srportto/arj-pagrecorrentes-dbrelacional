package br.com.srportto.temporizaautorizacao.application.usecase;

import br.com.srportto.temporizaautorizacao.domain.port.out.DecisaoAutorizacaoClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do ProcessarExpiracaoService")
class ProcessarExpiracaoServiceTest {

    @Mock
    private DecisaoAutorizacaoClient decisaoAutorizacaoClient;

    @Test
    @DisplayName("processar aciona o client com o UUID recebido")
    void processarAcionaClient() {
        var useCase = new ProcessarExpiracaoService(decisaoAutorizacaoClient);
        var id = UUID.randomUUID();

        useCase.processar(id);

        verify(decisaoAutorizacaoClient).expirar(id);
    }

}
