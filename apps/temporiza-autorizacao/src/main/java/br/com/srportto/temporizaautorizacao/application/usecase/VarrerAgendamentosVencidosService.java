package br.com.srportto.temporizaautorizacao.application.usecase;

import br.com.srportto.temporizaautorizacao.domain.port.in.VarrerAgendamentosVencidosUseCase;
import br.com.srportto.temporizaautorizacao.domain.port.out.AgendamentoRepository;
import br.com.srportto.temporizaautorizacao.infrastructure.config.TemporizacaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Orquestra a varredura periódica: pergunta à porta {@link AgendamentoRepository} quantos
 * agendamentos vencidos foram movidos para o stream de expirações e loga o resultado. A
 * atomicidade (script Lua) e a eleição de instância vivem inteiramente no adaptador
 * ({@code ValkeyAgendamentoRepository}) — este serviço não conhece Redis/Valkey.
 */
@Service
public class VarrerAgendamentosVencidosService implements VarrerAgendamentosVencidosUseCase {

    private static final Logger log = LoggerFactory.getLogger(VarrerAgendamentosVencidosService.class);

    private final AgendamentoRepository repository;
    private final TemporizacaoProperties properties;

    public VarrerAgendamentosVencidosService(AgendamentoRepository repository, TemporizacaoProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public void varrer() {
        int movidos = repository.moverVencidosParaExpiracao(Instant.now(), properties.varreduraLote());

        if (movidos > 0) {
            log.info("Varredura moveu {} agendamento(s) vencido(s) para o stream de expirações", movidos);
        }
    }

}
