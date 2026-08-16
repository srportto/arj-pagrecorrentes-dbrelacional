package br.com.srportto.temporizaautorizacao.infrastructure.scheduler;

import br.com.srportto.temporizaautorizacao.domain.port.in.VarrerAgendamentosVencidosUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do VarreduraAgendamentoScheduler")
class VarreduraAgendamentoSchedulerTest {

    @Mock
    private VarrerAgendamentosVencidosUseCase useCase;

    @Test
    @DisplayName("executar delega ao use case de varredura")
    void executarDelegaAoUseCase() {
        var scheduler = new VarreduraAgendamentoScheduler(useCase);

        scheduler.executar();

        verify(useCase).varrer();
    }

}
