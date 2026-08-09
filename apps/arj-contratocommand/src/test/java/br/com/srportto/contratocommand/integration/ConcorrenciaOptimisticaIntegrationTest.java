package br.com.srportto.contratocommand.integration;

import br.com.srportto.contratocommand.application.AutorizacaoRepository;
import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.cancelamento.CancelarAutorizacaoUseCase;
import br.com.srportto.contratocommand.application.cancelamento.CancelamentoContext;
import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.utilities.IdContaUUIDPartitionDistributor;
import br.com.srportto.contratocommand.domain.utilities.ReversibleUUIDv7;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de integração de concorrência real: dispara dois cancelamentos simultâneos sobre a mesma
 * autorização em threads distintas, verificando empiricamente se o lock otimista via @Version
 * dispara OptimisticLockException na segunda transação. Ponto central do design D5/D1 de
 * `integridade-fluxo-escrita` — sem esta validação real (não mockada), não há evidência de que a
 * correção funciona.
 *
 * Usa Testcontainers (Postgres vanilla, não a imagem custom com pg_partman/pg_cron — este teste
 * não exercita expurgo automatizado, só o comportamento de @Version sob concorrência real, então
 * um schema minimalista com partição DEFAULT é suficiente e evita o custo de build da imagem
 * custom). Hermético e reprodutível: sobe/derruba o container sozinho, não depende de Postgres
 * local rodando.
 *
 * Gerenciamento manual do container (em vez de {@code @Testcontainers}/{@code @Container}): em
 * ambientes sem Docker acessível pela API Java do Testcontainers (mesmo com o Docker CLI
 * funcionando — ex.: alguns sandboxes de CI), a classe inteira é desabilitada via
 * {@link DockerDisponivelCondition}, reportado como "Skipped" pelo Surefire — visível no
 * resumo do build, ao contrário de {@code Assumptions.assumeTrue} num {@code @BeforeAll} de
 * {@code @SpringBootTest}, que aborta silenciosamente como "Tests run: 0".
 */
@SpringBootTest
@ExtendWith(DockerDisponivelCondition.class)
@DisplayName("Teste de integração: concorrência otimista no cancelamento")
class ConcorrenciaOptimisticaIntegrationTest {

    static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgres.getUsername());
        registry.add("spring.datasource.password", () -> postgres.getPassword());
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @BeforeAll
    static void subirContainerECriarSchema() throws Exception {
        postgres = new PostgreSQLContainer<>("postgres:18")
                .withDatabaseName("db-csp-postgres-test")
                .withUsername("docker")
                .withPassword("test-only-nao-e-segredo-real");
        postgres.start();

        try (Connection conn = postgres.createConnection("");
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE autorizacoes (
                    id_autorizacao UUID NOT NULL,
                    id_particao_conta INT NOT NULL,
                    tipo_produto NUMERIC(6,0) NOT NULL,
                    tipo_jornada NUMERIC(6,0) NOT NULL DEFAULT 0,
                    status INT NOT NULL,
                    motivo_status TEXT,
                    data_hora_inclusao timestamp NOT NULL,
                    data_hora_ultima_atlz timestamp NOT NULL,
                    data_inicio_vigencia DATE,
                    data_fim_vigencia DATE NOT NULL,
                    valor NUMERIC(17, 2),
                    id_autorizacao_empresa TEXT,
                    valor_limite NUMERIC(17, 2),
                    frequencia INT CHECK (frequencia IN (1, 2, 3, 4)),
                    quantidade_dividas_ciclo INT,
                    indicador_uso_limite_conta INT,
                    indicador_tipo_mensageria INT,
                    codigo_canal_contratacao TEXT NOT NULL,
                    descricao TEXT,
                    id_unico_conta_contratante UUID,
                    id_pessoa_pagadora UUID,
                    id_pessoa_devedora UUID,
                    id_pessoa_recebedora UUID,
                    codigo_canal_cancelamento TEXT,
                    id_pessoa_cancelamento UUID,
                    data_hora_cancelamento timestamp,
                    motivo_cancelamento TEXT,
                    metadados JSON,
                    version BIGINT NOT NULL DEFAULT 0,
                    CONSTRAINT pk_autorizacoees PRIMARY KEY (id_autorizacao, id_particao_conta),
                    CONSTRAINT uq_autorizacoes_empresa UNIQUE (id_particao_conta, id_autorizacao_empresa)
                ) PARTITION BY LIST (id_particao_conta);
                """);
            stmt.execute("CREATE TABLE autorizacoes_default PARTITION OF autorizacoes DEFAULT;");
        }
    }

    @AfterAll
    static void pararContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Autowired
    private AutorizacaoRepository repository;

    @Autowired
    private CancelarAutorizacaoUseCase cancelarUseCase;

    @Test
    @DisplayName("Dois cancelamentos concorrentes: ao menos uma transacao deve falhar com OptimisticLockException (as duas podem falhar)")
    void doisCancelamentosConcorrentes_AoMenosUmaFalhaComLockException() throws InterruptedException {
        Autorizacao aut = criarAutorizacaoTeste();
        repository.saveAndFlush(aut);
        UUID idAutorizacao = aut.getIdAutorizacao().getIdAutorizacao();

        CountDownLatch podeComecar = new CountDownLatch(1);
        CountDownLatch terminou = new CountDownLatch(2);
        AtomicReference<Exception> primeiroErro = new AtomicReference<>();
        AtomicReference<Exception> segundoErro = new AtomicReference<>();

        Thread thread1 = new Thread(() -> {
            try {
                podeComecar.await();
                CancelamentoContext ctx = TestFixtures.cancelarContext(idAutorizacao.toString(), TipoProduto.PIX_AUTO);
                cancelarUseCase.execute(ctx);
            } catch (Exception e) {
                primeiroErro.set(e);
            } finally {
                terminou.countDown();
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                podeComecar.await();
                CancelamentoContext ctx = TestFixtures.cancelarContext(idAutorizacao.toString(), TipoProduto.PIX_AUTO);
                cancelarUseCase.execute(ctx);
            } catch (Exception e) {
                segundoErro.set(e);
            } finally {
                terminou.countDown();
            }
        });

        thread1.start();
        thread2.start();
        podeComecar.countDown();
        boolean terminouATempo = terminou.await(30, TimeUnit.SECONDS);
        assertTrue(terminouATempo, "As threads de cancelamento concorrente não terminaram em 30s — possível deadlock");

        boolean algumaFalhouComLock =
                causaContemLockException(primeiroErro.get()) || causaContemLockException(segundoErro.get());

        assertTrue(algumaFalhouComLock,
                "Nenhuma das transacoes concorrentes disparou OptimisticLockException/ObjectOptimisticLockingFailureException. "
                        + "Primeiro erro: " + primeiroErro.get() + ", Segundo erro: " + segundoErro.get());
    }

    private boolean causaContemLockException(Throwable t) {
        while (t != null) {
            if (t instanceof ObjectOptimisticLockingFailureException
                    || t instanceof jakarta.persistence.OptimisticLockException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private Autorizacao criarAutorizacaoTeste() {
        UUID idUnicoConta = UUID.randomUUID();
        int particao = IdContaUUIDPartitionDistributor.getPartitionFast(idUnicoConta);
        UUID idAutorizacao = ReversibleUUIDv7.generate(particao);

        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(new IdAutorizacao(idAutorizacao, particao));
        aut.setIdAutorizacaoEmpresa("test-emp-" + UUID.randomUUID());
        aut.setTipoProduto(TipoProduto.PIX_AUTO);
        aut.setTipoJornada(TipoJornadaAutorizacao.SPI_J1);
        aut.setStatus((int) StatusAutorizacao.ATIVA.getStatusAutorizacao());
        aut.setMotivoStatus("Ativo");
        aut.setDataInicioVigencia(LocalDate.now());
        aut.setDataFimVigencia(LocalDate.of(9999, 12, 31));
        aut.setDataHoraInclusao(LocalDateTime.now());
        aut.setDataHoraUltimaAtualizacao(LocalDateTime.now());
        aut.setValorAutorizacao(new BigDecimal("1000.00"));
        aut.setValorLimite(new BigDecimal("5000.00"));
        aut.setFrequenciaPagamento((short) 1);
        aut.setQuantidadeDividasCiclo((short) 1);
        aut.setIndicadorUsoLimiteConta((short) 0);
        aut.setIndicadorTipoMensageria((short) 0);
        aut.setCodigoCanalContratacao("C1");
        aut.setIdUnicoContaContratante(idUnicoConta);
        aut.setIdPessoaPagadora(UUID.randomUUID());
        aut.setIdPessoaDevedora(UUID.randomUUID());
        aut.setIdPessoaRecebedora(UUID.randomUUID());
        aut.setMetadados("{}");
        // NÃO setar version explicitamente: Spring Data JPA usa version == null para decidir se a
        // entidade é nova (persist) ou existente (merge/update). Setar 0L faz o save() tentar um
        // UPDATE numa linha inexistente e falhar com StaleObjectStateException já no setup do teste.

        return aut;
    }
}
