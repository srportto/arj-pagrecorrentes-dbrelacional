package br.com.srportto.temporizaautorizacao.application.agendamento;

import br.com.srportto.temporizaautorizacao.shared.config.TemporizacaoProperties;
import br.com.srportto.temporizaautorizacao.shared.exceptions.AgendamentoInvalidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do AgendarExpiracaoUseCase")
class AgendarExpiracaoUseCaseTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    private final TemporizacaoProperties properties = new TemporizacaoProperties(
            10, 5000, 100, 120000, "agenda:{pixauto:j1}", "stream:{pixauto:j1}:expiracoes",
            "temporizaautorizacao", "worker-1", "http://localhost:8080", 5000);

    private final AgendarExpiracaoUseCase useCase;

    AgendarExpiracaoUseCaseTest() {
        this.useCase = new AgendarExpiracaoUseCase(null, properties);
    }

    @Test
    @DisplayName("agenda o vencimento como data_hora_inclusao + prazo configurado")
    void agendaComVencimentoCorreto() {
        var useCaseComMock = new AgendarExpiracaoUseCase(agendamentoRepository, properties);
        var id = UUID.randomUUID();
        var inclusao = LocalDateTime.of(2026, 8, 8, 10, 0, 0);
        var json = payloadJson(id, inclusao);

        useCaseComMock.agendar(json);

        var vencimentoEsperado = inclusao.plusMinutes(10).atZone(ZoneId.systemDefault()).toInstant();
        var captor = ArgumentCaptor.forClass(Instant.class);
        verify(agendamentoRepository).agendar(org.mockito.ArgumentMatchers.eq(id), captor.capture());
        assertEquals(vencimentoEsperado, captor.getValue());
    }

    @Test
    @DisplayName("reentrega do mesmo evento produz o mesmo vencimento (não adiado pelo instante de consumo)")
    void reentregaNaoAdiaVencimento() {
        var useCaseComMock = new AgendarExpiracaoUseCase(agendamentoRepository, properties);
        var id = UUID.randomUUID();
        var inclusao = LocalDateTime.of(2026, 8, 8, 10, 0, 0);
        var json = payloadJson(id, inclusao);

        useCaseComMock.agendar(json);
        useCaseComMock.agendar(json);

        var captor = ArgumentCaptor.forClass(Instant.class);
        verify(agendamentoRepository, times(2)).agendar(org.mockito.ArgumentMatchers.eq(id), captor.capture());
        assertEquals(captor.getAllValues().get(0), captor.getAllValues().get(1));
    }

    @Test
    @DisplayName("payload sem id_autorizacao lança AgendamentoInvalidoException")
    void semIdAutorizacaoLanca() {
        var json = "{\"data_hora_inclusao\":\"2026-08-08T10:00:00\"}";
        assertThrows(AgendamentoInvalidoException.class, () -> useCase.agendar(json));
    }

    @Test
    @DisplayName("payload sem data_hora_inclusao lança AgendamentoInvalidoException")
    void semDataHoraInclusaoLanca() {
        var json = "{\"id_autorizacao\":\"" + UUID.randomUUID() + "\"}";
        assertThrows(AgendamentoInvalidoException.class, () -> useCase.agendar(json));
    }

    @Test
    @DisplayName("JSON malformado lança AgendamentoInvalidoException")
    void jsonMalformadoLanca() {
        assertThrows(AgendamentoInvalidoException.class, () -> useCase.agendar("{isto nao e json"));
    }

    private String payloadJson(UUID id, LocalDateTime inclusao) {
        return "{\"id_autorizacao\":\"" + id + "\",\"data_hora_inclusao\":\"" + inclusao + "\"}";
    }

}
