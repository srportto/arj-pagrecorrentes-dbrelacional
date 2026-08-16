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
    @DisplayName("processar converte a string para UUID e aciona o client")
    void processarAcionaClient() {
        var useCase = new ProcessarExpiracaoService(decisaoAutorizacaoClient);
        var id = UUID.randomUUID();

        useCase.processar(id.toString());

        verify(decisaoAutorizacaoClient).expirar(id);
    }

}
