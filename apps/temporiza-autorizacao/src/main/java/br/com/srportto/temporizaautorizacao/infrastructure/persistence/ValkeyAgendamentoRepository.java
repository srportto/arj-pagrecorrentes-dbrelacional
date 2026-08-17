package br.com.srportto.temporizaautorizacao.infrastructure.persistence;

import br.com.srportto.temporizaautorizacao.domain.port.out.AgendamentoRepository;
import br.com.srportto.temporizaautorizacao.infrastructure.config.TemporizacaoProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Adapter de saída: ZADD no sorted set que funciona como relógio de vencimentos, e a varredura
 * atômica (script Lua) que promove os vencidos ao stream de expirações — a eleição de qual
 * instância processa cada id (o {@code ZREM} dentro do script) é detalhe de infraestrutura,
 * nunca sobe para {@code application}.
 */
@Component
public class ValkeyAgendamentoRepository implements AgendamentoRepository {

    private static final RedisScript<Long> SCRIPT_VARREDURA = criarScriptVarredura();

    private final StringRedisTemplate redisTemplate;
    private final TemporizacaoProperties properties;

    public ValkeyAgendamentoRepository(StringRedisTemplate redisTemplate, TemporizacaoProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void agendar(UUID idAutorizacao, Instant vencimento) {
        redisTemplate.opsForZSet().add(
                properties.chaveAgenda(),
                idAutorizacao.toString(),
                vencimento.toEpochMilli());
    }

    @Override
    public int moverVencidosParaExpiracao(Instant agora, int lote) {
        Long movidos = redisTemplate.execute(SCRIPT_VARREDURA,
                List.of(properties.chaveAgenda(), properties.chaveStream()),
                String.valueOf(agora.toEpochMilli()), String.valueOf(lote));
        return movidos == null ? 0 : movidos.intValue();
    }

    private static RedisScript<Long> criarScriptVarredura() {
        var script = new DefaultRedisScript<Long>();
        script.setResultType(Long.class);
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/varredura.lua")));
        return script;
    }

}
