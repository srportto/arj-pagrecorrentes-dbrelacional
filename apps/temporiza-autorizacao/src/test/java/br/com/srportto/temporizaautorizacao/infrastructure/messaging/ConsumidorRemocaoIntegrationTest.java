package br.com.srportto.temporizaautorizacao.infrastructure.messaging;

import br.com.srportto.temporizaautorizacao.application.usecase.ProcessarExpiracaoService;
import br.com.srportto.temporizaautorizacao.infrastructure.config.TemporizacaoProperties;
import br.com.srportto.temporizaautorizacao.infrastructure.scheduler.ConsumidoresOrfaosLimpezaScheduler;
import br.com.srportto.temporizaautorizacao.infrastructure.web.TemporizacaoHealthIndicator;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exige o Valkey local no ar (ver infra/local/redis). Limiares de ociosidade artificialmente pequenos (ms, não min). */
@DisplayName("Testes de integração: remoção de consumidores órfãos contra o Valkey real")
class ConsumidorRemocaoIntegrationTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private final TemporizacaoProperties properties = new TemporizacaoProperties(
            10, 5000, 100, 50,
            "test:agenda:" + UUID.randomUUID(), "test:stream:" + UUID.randomUUID(),
            "temporizaautorizacao", "worker-1", "http://localhost:8080", 5000, 200);

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

    /** Publica primeiro (cria o stream) e só depois cria o grupo — XGROUP CREATE exige o stream já existir. */
    private void publicarECriarGrupo(String idAutorizacao) {
        redisTemplate.opsForStream().add(properties.chaveStream(), Map.of("id_autorizacao", idAutorizacao));
        redisTemplate.opsForStream().createGroup(properties.chaveStream(), ReadOffset.from("0"), properties.grupoConsumidor());
    }

    private void publicar(String idAutorizacao) {
        redisTemplate.opsForStream().add(properties.chaveStream(), Map.of("id_autorizacao", idAutorizacao));
    }

    /** Lê (XREADGROUP) e, se {@code confirmar}, confirma (XACK) na sequência — registra o consumidor no grupo. */
    private void lerComo(String consumidorId, boolean confirmar) {
        var consumer = Consumer.from(properties.grupoConsumidor(), consumidorId);
        var lidas = redisTemplate.opsForStream().read(consumer,
                StreamOffset.create(properties.chaveStream(), ReadOffset.lastConsumed()));
        if (confirmar && lidas != null) {
            for (var record : lidas) {
                redisTemplate.opsForStream().acknowledge(properties.chaveStream(), properties.grupoConsumidor(), record.getId());
            }
        }
    }

    private boolean consumidorExiste(String consumidorId) {
        return redisTemplate.opsForStream().consumers(properties.chaveStream(), properties.grupoConsumidor())
                .stream().anyMatch(c -> consumidorId.equals(c.consumerName()));
    }

    @Test
    @DisplayName("consumidor ocioso além do limiar e sem pendência é removido; instância viva não é")
    void consumidorOciosoSemPendenciaRemovidoInstanciaVivaMantida() throws InterruptedException {
        publicarECriarGrupo(UUID.randomUUID().toString());
        lerComo("morto", true); // pending 0 após ack — candidato a órfão

        Thread.sleep(properties.consumidorOciosoLimiteMs() + 50); // ultrapassa o limiar

        publicar(UUID.randomUUID().toString());
        lerComo("vivo", true); // acabou de interagir — idle ~0, bem abaixo do limiar

        var remocaoService = new ConsumidorRemocaoService(redisTemplate, properties);
        var scheduler = new ConsumidoresOrfaosLimpezaScheduler(redisTemplate, remocaoService, properties);
        scheduler.removerConsumidoresOrfaos();

        assertFalse(consumidorExiste("morto"), "consumidor ocioso além do limiar e sem pendência deveria ter sido removido");
        assertTrue(consumidorExiste("vivo"), "consumidor recém-ativo não deveria ser removido");
    }

    @Test
    @DisplayName("consumidor ocioso COM pendência nunca é removido, por mais ocioso que esteja")
    void consumidorOciosoComPendenciaNuncaRemovido() throws InterruptedException {
        publicarECriarGrupo(UUID.randomUUID().toString());
        lerComo("orfao-com-pel", false); // NÃO confirma — fica com pending = 1

        Thread.sleep(properties.consumidorOciosoLimiteMs() + 50);

        var remocaoService = new ConsumidorRemocaoService(redisTemplate, properties);
        var scheduler = new ConsumidoresOrfaosLimpezaScheduler(redisTemplate, remocaoService, properties);
        scheduler.removerConsumidoresOrfaos();

        assertTrue(consumidorExiste("orfao-com-pel"),
                "consumidor com PEL não vazio NUNCA deve ser removido, mesmo ocioso além do limiar");

        var pendentes = redisTemplate.opsForStream().pending(properties.chaveStream(),
                Consumer.from(properties.grupoConsumidor(), "orfao-com-pel"), Range.unbounded(), 10);
        assertEquals(1, pendentes.size(), "a entrada pendente não deve ter sido descartada");
    }

    @Test
    @DisplayName("entrada de instância morta continua reivindicável após a limpeza rodar, e é reprocessada")
    void entradaSobreviveALimpezaEContinuaReivindicavel() throws InterruptedException {
        var idAutorizacao = UUID.randomUUID();
        publicarECriarGrupo(idAutorizacao.toString());
        lerComo("instancia-morta", false); // fica com pending = 1, nunca confirma (simulando SIGKILL)

        Thread.sleep(properties.consumidorOciosoLimiteMs() + 50);

        // 1) limpeza roda primeiro: consumidor tem pendência, não é removido
        var remocaoService = new ConsumidorRemocaoService(redisTemplate, properties);
        var scheduler = new ConsumidoresOrfaosLimpezaScheduler(redisTemplate, remocaoService, properties);
        scheduler.removerConsumidoresOrfaos();
        assertTrue(consumidorExiste("instancia-morta"), "ainda tem pendência — não pode ter sido removido");

        // 2) reivindicador (min-idle bem menor que o limiar de limpeza) reivindica e processa a entrada
        var processadas = new CopyOnWriteArrayList<UUID>();
        var useCase = new ProcessarExpiracaoService(processadas::add);
        var listener = new ExpiracaoStreamListener(useCase, redisTemplate, properties);
        var reivindicador = new PendenciasSchedulerReivindicador(redisTemplate, listener, properties);
        reivindicador.reivindicarPendenciasOciosas();

        assertEquals(1, processadas.size(), "a entrada deveria ter sido reivindicada e reprocessada");
        assertEquals(idAutorizacao, processadas.get(0));

        // 3) agora o consumidor original está com pending = 0 — a limpeza seguinte pode removê-lo
        Thread.sleep(properties.consumidorOciosoLimiteMs() + 50);
        scheduler.removerConsumidoresOrfaos();
        assertFalse(consumidorExiste("instancia-morta"), "sem pendência e ocioso, o órfão original agora é removível");
    }

    @Test
    @DisplayName("duas remoções concorrentes do mesmo órfão terminam sem erro e sem lock")
    void remocaoConcorrenteNaoGeraErro() throws InterruptedException {
        publicarECriarGrupo(UUID.randomUUID().toString());
        lerComo("orfao-disputado", true); // pending 0

        Thread.sleep(properties.consumidorOciosoLimiteMs() + 50);

        var remocaoService = new ConsumidorRemocaoService(redisTemplate, properties);
        int tentativas = 5;
        var executor = Executors.newFixedThreadPool(tentativas);
        var latch = new CountDownLatch(tentativas);
        var erros = new CopyOnWriteArrayList<Throwable>();

        for (int i = 0; i < tentativas; i++) {
            executor.submit(() -> {
                try {
                    remocaoService.removerSeSemPendencia("orfao-disputado", 0);
                } catch (Throwable t) {
                    erros.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "remoções concorrentes não terminaram a tempo");
        executor.shutdown();
        assertTrue(erros.isEmpty(), "nenhuma remoção concorrente deveria lançar exceção: " + erros);
        assertFalse(consumidorExiste("orfao-disputado"));
    }

    @Test
    @DisplayName("remover consumidor inexistente é no-op, sem lançar exceção")
    void removerInexistenteNaoLanca() {
        publicarECriarGrupo(UUID.randomUUID().toString());
        var remocaoService = new ConsumidorRemocaoService(redisTemplate, properties);
        assertDoesNotThrow(() -> remocaoService.removerSeSemPendencia("nunca-existiu", 0));
    }

    @Test
    @DisplayName("contagem de consumidores é exposta no health indicator")
    void contagemDeConsumidoresExposta() {
        publicarECriarGrupo(UUID.randomUUID().toString());
        lerComo("worker-a", true);
        lerComo("worker-b", true);

        var indicator = criarHealthIndicatorSaudavel();
        var detalhes = indicator.health().getDetails();

        assertEquals(2, detalhes.get("consumidoresStream"));
    }

    @Test
    @DisplayName("divergência entre consumidores e instâncias vivas não derruba o /actuator/health")
    void divergenciaNaoDerrubaHealth() {
        publicarECriarGrupo(UUID.randomUUID().toString());
        // simula órfãos: 3 consumidores registrados, nenhum vivo de verdade
        lerComo("residuo-1", true);
        lerComo("residuo-2", true);
        lerComo("residuo-3", true);

        var indicator = criarHealthIndicatorSaudavel();

        assertEquals(Status.UP, indicator.health().getStatus(),
                "divergência entre consumidores registrados e instâncias vivas não é falha de saúde");
    }

    private TemporizacaoHealthIndicator criarHealthIndicatorSaudavel() {
        var registry = mock(MessageListenerContainerRegistry.class);
        when(registry.isRunning()).thenReturn(false); // evita mockar containers; shutdown intencional não é falha
        return new TemporizacaoHealthIndicator(registry, connectionFactory, redisTemplate, properties);
    }

}
