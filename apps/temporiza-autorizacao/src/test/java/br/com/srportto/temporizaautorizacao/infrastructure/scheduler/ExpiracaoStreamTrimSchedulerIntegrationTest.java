package br.com.srportto.temporizaautorizacao.infrastructure.scheduler;

import br.com.srportto.temporizaautorizacao.infrastructure.config.TemporizacaoProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exige o Valkey local no ar (ver infra/local/redis). Prova a correção do achado crítico da
 * auditoria: sem poda, {@code XACK} só remove do PEL — nunca do stream — e o stream cresce
 * indefinidamente. Cobre as duas garantias que {@link ExpiracaoStreamTrimScheduler} precisa
 * oferecer: entradas confirmadas somem, entradas ainda pendentes nunca são removidas.
 */
@DisplayName("Testes de integração: poda do stream de expirações contra o Valkey real")
class ExpiracaoStreamTrimSchedulerIntegrationTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private final TemporizacaoProperties properties = new TemporizacaoProperties(
            10, 5000, 100, 120000,
            "test:agenda:" + UUID.randomUUID(), "test:stream:" + UUID.randomUUID(),
            "temporizaautorizacao", "worker-1", "http://localhost:8080", 5000, 600000);

    @BeforeAll
    static void subirConexao() {
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void encerrarConexao() {
        connectionFactory.destroy();
    }

    @AfterEach
    void limparChaves() {
        redisTemplate.delete(properties.chaveStream());
    }

    @Test
    @DisplayName("entradas confirmadas (XACK) são removidas do stream pela poda")
    void entradasConfirmadas_SaoRemovidas() {
        var scheduler = new ExpiracaoStreamTrimScheduler(redisTemplate, properties);
        redisTemplate.opsForStream().createGroup(properties.chaveStream(), ReadOffset.from("0"),
                properties.grupoConsumidor());

        adicionar("id-1");
        adicionar("id-2");
        adicionar("id-3");

        var consumer = Consumer.from(properties.grupoConsumidor(), properties.consumidorId());
        List<MapRecord<String, Object, Object>> lidas = redisTemplate.opsForStream().read(consumer,
                StreamReadOptions.empty().count(10),
                StreamOffset.create(properties.chaveStream(), ReadOffset.lastConsumed()));
        assertEquals(3, lidas.size(), "as três entradas precisam ter entrado no PEL para o teste fazer sentido");

        // Confirma todas — PEL fica vazio, então a poda usa o último id entregue como limite.
        for (var registro : lidas) {
            redisTemplate.opsForStream().acknowledge(properties.chaveStream(), properties.grupoConsumidor(),
                    registro.getId().getValue());
        }

        scheduler.podarEntradasConfirmadas();

        List<MapRecord<String, Object, Object>> restantes = redisTemplate.<Object, Object>opsForStream()
                .range(properties.chaveStream(), Range.unbounded());
        assertTrue(restantes.isEmpty(), "todas as entradas confirmadas deveriam ter sido removidas do stream");
    }

    @Test
    @DisplayName("entrada ainda pendente no PEL nunca é removida pela poda")
    void entradaPendente_NuncaERemovida() {
        var scheduler = new ExpiracaoStreamTrimScheduler(redisTemplate, properties);
        redisTemplate.opsForStream().createGroup(properties.chaveStream(), ReadOffset.from("0"),
                properties.grupoConsumidor());

        adicionar("id-confirmado");
        adicionar("id-pendente");
        adicionar("id-depois-do-pendente");

        var consumer = Consumer.from(properties.grupoConsumidor(), properties.consumidorId());
        List<MapRecord<String, Object, Object>> lidas = redisTemplate.opsForStream().read(consumer,
                StreamReadOptions.empty().count(10),
                StreamOffset.create(properties.chaveStream(), ReadOffset.lastConsumed()));
        assertEquals(3, lidas.size());

        // Confirma só a primeira — as outras duas (inclusive a que vem depois da pendente) ficam no PEL.
        redisTemplate.opsForStream().acknowledge(properties.chaveStream(), properties.grupoConsumidor(),
                lidas.get(0).getId().getValue());

        scheduler.podarEntradasConfirmadas();

        List<MapRecord<String, Object, Object>> restantes = redisTemplate.<Object, Object>opsForStream()
                .range(properties.chaveStream(), Range.unbounded());

        assertEquals(2, restantes.size(),
                "a entrada pendente e a que vem depois dela precisam sobreviver à poda");
        assertEquals(lidas.get(1).getId(), restantes.get(0).getId());
        assertEquals(lidas.get(2).getId(), restantes.get(1).getId());
    }

    private void adicionar(String idAutorizacao) {
        redisTemplate.opsForStream().add(properties.chaveStream(), Map.of("id_autorizacao", idAutorizacao));
    }
}
