package br.com.srportto.temporizaautorizacao.infrastructure.messaging;

import br.com.srportto.temporizaautorizacao.domain.exception.AgendamentoInvalidoException;
import br.com.srportto.temporizaautorizacao.domain.port.in.AgendarExpiracaoUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do TemporizacaoEventoListener")
class TemporizacaoEventoListenerTest {

    @Mock
    private AgendarExpiracaoUseCase useCase;

    @Test
    @DisplayName("receber desserializa o payload e delega ao use case com id e data de inclusão")
    void receberDesserializaEDelegaAoUseCase() {
        var listener = new TemporizacaoEventoListener(useCase);
        var id = UUID.randomUUID();
        var inclusao = LocalDateTime.of(2026, 8, 8, 10, 0, 0);
        var json = "{\"id_autorizacao\":\"" + id + "\",\"data_hora_inclusao\":\"" + inclusao + "\"}";

        listener.receber(json);

        verify(useCase).agendar(id, inclusao);
    }

    @Test
    @DisplayName("JSON malformado lança AgendamentoInvalidoException")
    void jsonMalformadoLanca() {
        var listener = new TemporizacaoEventoListener(useCase);

        assertThrows(AgendamentoInvalidoException.class, () -> listener.receber("{isto nao e json"));
    }

}
