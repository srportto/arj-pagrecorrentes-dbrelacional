package br.com.srportto.temporizaautorizacao.application.usecase;

import br.com.srportto.temporizaautorizacao.domain.exception.AgendamentoInvalidoException;
import br.com.srportto.temporizaautorizacao.domain.port.out.AgendamentoRepository;
import br.com.srportto.temporizaautorizacao.infrastructure.config.TemporizacaoProperties;
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
@DisplayName("Testes do AgendarExpiracaoService")
class AgendarExpiracaoServiceTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    private final TemporizacaoProperties properties = new TemporizacaoProperties(
            10, 5000, 100, 120000, "agenda:{pixauto:j1}", "stream:{pixauto:j1}:expiracoes",
            "temporizaautorizacao", "worker-1", "http://localhost:8080", 5000, 600000);

    private final AgendarExpiracaoService useCase;

    AgendarExpiracaoServiceTest() {
        this.useCase = new AgendarExpiracaoService(null, properties);
    }

    @Test
    @DisplayName("agenda o vencimento como data_hora_inclusao + prazo configurado")
    void agendaComVencimentoCorreto() {
        var useCaseComMock = new AgendarExpiracaoService(agendamentoRepository, properties);
        var id = UUID.randomUUID();
        var inclusao = LocalDateTime.of(2026, 8, 8, 10, 0, 0);

        useCaseComMock.agendar(id, inclusao);

        var vencimentoEsperado = inclusao.plusMinutes(10).atZone(ZoneId.systemDefault()).toInstant();
        var captor = ArgumentCaptor.forClass(Instant.class);
        verify(agendamentoRepository).agendar(org.mockito.ArgumentMatchers.eq(id), captor.capture());
        assertEquals(vencimentoEsperado, captor.getValue());
    }

    @Test
    @DisplayName("reentrega do mesmo evento produz o mesmo vencimento (não adiado pelo instante de consumo)")
    void reentregaNaoAdiaVencimento() {
        var useCaseComMock = new AgendarExpiracaoService(agendamentoRepository, properties);
        var id = UUID.randomUUID();
        var inclusao = LocalDateTime.of(2026, 8, 8, 10, 0, 0);

        useCaseComMock.agendar(id, inclusao);
        useCaseComMock.agendar(id, inclusao);

        var captor = ArgumentCaptor.forClass(Instant.class);
        verify(agendamentoRepository, times(2)).agendar(org.mockito.ArgumentMatchers.eq(id), captor.capture());
        assertEquals(captor.getAllValues().get(0), captor.getAllValues().get(1));
    }

    @Test
    @DisplayName("sem idAutorizacao lança AgendamentoInvalidoException")
    void semIdAutorizacaoLanca() {
        var inclusao = LocalDateTime.of(2026, 8, 8, 10, 0, 0);
        assertThrows(AgendamentoInvalidoException.class, () -> useCase.agendar(null, inclusao));
    }

    @Test
    @DisplayName("sem dataHoraInclusao lança AgendamentoInvalidoException")
    void semDataHoraInclusaoLanca() {
        var id = UUID.randomUUID();
        assertThrows(AgendamentoInvalidoException.class, () -> useCase.agendar(id, null));
    }

}
