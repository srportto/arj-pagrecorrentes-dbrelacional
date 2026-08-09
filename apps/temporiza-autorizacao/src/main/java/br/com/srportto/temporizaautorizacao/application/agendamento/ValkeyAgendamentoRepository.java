package br.com.srportto.temporizaautorizacao.application.agendamento;

import br.com.srportto.temporizaautorizacao.shared.config.TemporizacaoProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Adapter de saída: ZADD no sorted set que funciona como relógio de vencimentos. */
@Component
public class ValkeyAgendamentoRepository implements AgendamentoRepository {

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

}
