package br.com.srportto.temporizaautorizacao.application.varredura;

import br.com.srportto.temporizaautorizacao.shared.config.TemporizacaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Executa a varredura atômica (script Lua) que move vencidos do sorted set (agenda) para o
 * stream (fila de trabalho). Seguro para rodar concorrentemente em todas as instâncias, sem
 * lock distribuído externo — ver {@code varredura.lua} e a capacidade
 * {@code agendamento-expiracao-valkey}.
 */
@Service
public class VarrerAgendamentosVencidosUseCase {

    private static final Logger log = LoggerFactory.getLogger(VarrerAgendamentosVencidosUseCase.class);

    private static final RedisScript<Long> SCRIPT = criarScript();

    private final StringRedisTemplate redisTemplate;
    private final TemporizacaoProperties properties;

    public VarrerAgendamentosVencidosUseCase(StringRedisTemplate redisTemplate, TemporizacaoProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void varrer() {
        long agora = Instant.now().toEpochMilli();

        Long criados = redisTemplate.execute(SCRIPT,
                List.of(properties.chaveAgenda(), properties.chaveStream()),
                String.valueOf(agora), String.valueOf(properties.varreduraLote()));

        if (criados != null && criados > 0) {
            log.info("Varredura moveu {} agendamento(s) vencido(s) para o stream de expirações", criados);
        }
    }

    private static RedisScript<Long> criarScript() {
        var script = new DefaultRedisScript<Long>();
        script.setResultType(Long.class);
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/varredura.lua")));
        return script;
    }

}
