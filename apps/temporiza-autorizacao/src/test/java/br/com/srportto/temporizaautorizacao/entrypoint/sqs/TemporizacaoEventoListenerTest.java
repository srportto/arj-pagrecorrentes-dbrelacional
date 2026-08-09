package br.com.srportto.temporizaautorizacao.entrypoint.sqs;

import br.com.srportto.temporizaautorizacao.application.agendamento.AgendarExpiracaoUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do TemporizacaoEventoListener")
class TemporizacaoEventoListenerTest {

    @Mock
    private AgendarExpiracaoUseCase useCase;

    @Test
    @DisplayName("receber delega ao use case, sem try/catch")
    void receberDelegaAoUseCase() {
        var listener = new TemporizacaoEventoListener(useCase);
        var body = "{\"id_autorizacao\":\"x\"}";

        listener.receber(body);

        verify(useCase).agendar(body);
    }

}
