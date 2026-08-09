package br.com.srportto.temporizaautorizacao.application.expiracao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do ProcessarExpiracaoUseCase")
class ProcessarExpiracaoUseCaseTest {

    @Mock
    private DecisaoAutorizacaoClient decisaoAutorizacaoClient;

    @Test
    @DisplayName("processar converte a string para UUID e aciona o client")
    void processarAcionaClient() {
        var useCase = new ProcessarExpiracaoUseCase(decisaoAutorizacaoClient);
        var id = UUID.randomUUID();

        useCase.processar(id.toString());

        verify(decisaoAutorizacaoClient).expirar(id);
    }

}
