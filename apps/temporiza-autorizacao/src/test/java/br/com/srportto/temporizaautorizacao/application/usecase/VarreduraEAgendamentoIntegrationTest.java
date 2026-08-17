package br.com.srportto.temporizaautorizacao.application.usecase;

import br.com.srportto.temporizaautorizacao.infrastructure.config.TemporizacaoProperties;
import br.com.srportto.temporizaautorizacao.infrastructure.persistence.ValkeyAgendamentoRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exige o Valkey local no ar (ver infra/local/redis). */
@DisplayName("Testes de integração: agendamento + varredura contra o Valkey real")
class VarreduraEAgendamentoIntegrationTest {

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
        redisTemplate.delete(properties.chaveAgenda());
        redisTemplate.delete(properties.chaveStream());
    }

    @Test
    @DisplayName("agendamento é idempotente: reagendar o mesmo id sobrescreve o score, não duplica")
    void agendamentoIdempotente() {
        var repository = new ValkeyAgendamentoRepository(redisTemplate, properties);
        var id = UUID.randomUUID();

        repository.agendar(id, Instant.ofEpochMilli(1000));
        repository.agendar(id, Instant.ofEpochMilli(2000));

        Long tamanho = redisTemplate.opsForZSet().zCard(properties.chaveAgenda());
        Double score = redisTemplate.opsForZSet().score(properties.chaveAgenda(), id.toString());

        assertEquals(1L, tamanho, "reagendar o mesmo id não deve criar uma segunda entrada");
        assertEquals(2000.0, score, "o score deve refletir o último agendamento, não o primeiro");
    }

    @Test
    @DisplayName("varredura move vencidos para o stream e os remove da agenda")
    void varreduraMoveVencidosParaStream() {
        var useCase = new VarrerAgendamentosVencidosService(
                new ValkeyAgendamentoRepository(redisTemplate, properties), properties);
        var idVencido = UUID.randomUUID();
        var idFuturo = UUID.randomUUID();

        redisTemplate.opsForZSet().add(properties.chaveAgenda(), idVencido.toString(),
                Instant.now().minus(Duration.ofMinutes(1)).toEpochMilli());
        redisTemplate.opsForZSet().add(properties.chaveAgenda(), idFuturo.toString(),
                Instant.now().plus(Duration.ofHours(1)).toEpochMilli());

        useCase.varrer();

        assertTrue(redisTemplate.opsForZSet().score(properties.chaveAgenda(), idVencido.toString()) == null,
                "o id vencido deve ter sido removido da agenda");
        assertEquals(Instant.now().plus(Duration.ofHours(1)).toEpochMilli() / 1000,
                redisTemplate.opsForZSet().score(properties.chaveAgenda(), idFuturo.toString()).longValue() / 1000,
                "o id futuro deve permanecer na agenda");

        var entradas = redisTemplate.<String, String>opsForStream().range(properties.chaveStream(),
                org.springframework.data.domain.Range.unbounded());
        assertEquals(1, entradas.size());
        assertEquals(idVencido.toString(), entradas.get(0).getValue().get("id_autorizacao"));
    }

    @Test
    @DisplayName("varredura concorrente em múltiplas instâncias move cada id vencido exatamente uma vez")
    void varreduraConcorrenteElegeUnicoExecutor() throws InterruptedException {
        int totalIds = 50;
        var ids = new java.util.ArrayList<UUID>();
        for (int i = 0; i < totalIds; i++) {
            var id = UUID.randomUUID();
            ids.add(id);
            redisTemplate.opsForZSet().add(properties.chaveAgenda(), id.toString(),
                    Instant.now().minus(Duration.ofSeconds(1)).toEpochMilli());
        }

        var useCase = new VarrerAgendamentosVencidosService(
                new ValkeyAgendamentoRepository(redisTemplate, properties), properties);
        int instancias = 8;
        var executor = Executors.newFixedThreadPool(instancias);
        var latch = new CountDownLatch(instancias);
        var erros = new CopyOnWriteArrayList<Throwable>();

        for (int i = 0; i < instancias; i++) {
            executor.submit(() -> {
                try {
                    for (int r = 0; r < 5; r++) {
                        useCase.varrer();
                    }
                } catch (Throwable t) {
                    erros.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "varreduras concorrentes não terminaram a tempo");
        executor.shutdown();
        assertTrue(erros.isEmpty(), "nenhuma varredura deveria lançar exceção: " + erros);

        Long remanescentes = redisTemplate.opsForZSet().zCard(properties.chaveAgenda());
        assertEquals(0L, remanescentes, "todos os ids vencidos deveriam ter sido removidos da agenda");

        AtomicInteger totalNoStream = new AtomicInteger(0);
        List<UUID> vistos = new CopyOnWriteArrayList<>();
        var entradas = redisTemplate.<String, String>opsForStream().range(properties.chaveStream(),
                org.springframework.data.domain.Range.unbounded());
        for (var entrada : entradas) {
            totalNoStream.incrementAndGet();
            vistos.add(UUID.fromString(entrada.getValue().get("id_autorizacao")));
        }

        assertEquals(totalIds, totalNoStream.get(), "cada id vencido deve gerar exatamente uma entrada no stream");
        assertEquals(totalIds, vistos.stream().distinct().count(), "nenhum id deve aparecer duplicado no stream");
    }

}
