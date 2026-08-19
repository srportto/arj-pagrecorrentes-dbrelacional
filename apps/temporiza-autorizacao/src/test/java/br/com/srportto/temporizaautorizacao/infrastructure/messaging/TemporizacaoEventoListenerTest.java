package br.com.srportto.temporizaautorizacao.infrastructure.messaging;

import br.com.srportto.temporizaautorizacao.domain.exception.AgendamentoInvalidoException;
import br.com.srportto.temporizaautorizacao.domain.port.in.AgendarExpiracaoUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do TemporizacaoEventoListener")
class TemporizacaoEventoListenerTest {

    @Mock
    private AgendarExpiracaoUseCase useCase;

    @Test
    @DisplayName("receber desserializa o payload e delega ao use case com id e data de inclusão")
    void receberDesserializaEDelegaAoUseCase() {
        var listener = new TemporizacaoEventoListener(useCase, new tools.jackson.databind.ObjectMapper());
        var id = UUID.randomUUID();
        var inclusao = LocalDateTime.of(2026, 8, 8, 10, 0, 0);
        var json = "{\"id_autorizacao\":\"" + id + "\",\"data_hora_inclusao\":\"" + inclusao + "\"}";

        listener.receber(json);

        verify(useCase).agendar(id, inclusao);
    }

    @Test
    @DisplayName("JSON malformado lança AgendamentoInvalidoException")
    void jsonMalformadoLanca() {
        var listener = new TemporizacaoEventoListener(useCase, new tools.jackson.databind.ObjectMapper());

        assertThrows(AgendamentoInvalidoException.class, () -> listener.receber("{isto nao e json"));
    }

    @AfterEach
    void limpaMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("MDC é limpo após o processamento, com sucesso ou falha")
    void mdcELimpoAposProcessamento() {
        var listener = new TemporizacaoEventoListener(useCase, new tools.jackson.databind.ObjectMapper());
        var json = "{\"id_autorizacao\":\"" + UUID.randomUUID()
                + "\",\"data_hora_inclusao\":\"2026-08-08T10:00:00\"}";

        listener.receber(json);
        assertNull(MDC.get("traceId"), "MDC deve ser limpo após sucesso");

        assertThrows(AgendamentoInvalidoException.class, () -> listener.receber("{isto nao e json"));
        assertNull(MDC.get("traceId"), "MDC deve ser limpo mesmo após falha");
    }

    @Test
    @DisplayName("um traceId é populado no MDC durante o processamento da mensagem")
    void traceIdEPopuladoDuranteOProcessamento() {
        var listener = new TemporizacaoEventoListener(useCase, new tools.jackson.databind.ObjectMapper());
        doAnswer(invocation -> {
            assertTrue(MDC.get("traceId") != null && !MDC.get("traceId").isBlank());
            return null;
        }).when(useCase).agendar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        var json = "{\"id_autorizacao\":\"" + UUID.randomUUID()
                + "\",\"data_hora_inclusao\":\"2026-08-08T10:00:00\"}";

        listener.receber(json);

        verify(useCase).agendar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

}
