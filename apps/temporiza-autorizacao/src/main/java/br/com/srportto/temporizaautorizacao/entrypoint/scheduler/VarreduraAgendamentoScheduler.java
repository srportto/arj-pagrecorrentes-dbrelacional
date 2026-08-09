package br.com.srportto.temporizaautorizacao.entrypoint.scheduler;

import br.com.srportto.temporizaautorizacao.application.varredura.VarrerAgendamentosVencidosUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispara a varredura em intervalo fixo. Roda em todas as instâncias — a atomicidade do script Lua elege um único executor por id vencido. */
@Component
public class VarreduraAgendamentoScheduler {

    private final VarrerAgendamentosVencidosUseCase useCase;

    public VarreduraAgendamentoScheduler(VarrerAgendamentosVencidosUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(fixedDelayString = "${temporizacao.varredura-intervalo-ms}")
    public void executar() {
        useCase.varrer();
    }

}
