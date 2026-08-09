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
import br.com.srportto.contratocommand.domain.utilities.ControleExpurgoAutorizacao;
import br.com.srportto.contratocommand.domain.utilities.IdContaUUIDPartitionDistributor;
import br.com.srportto.contratocommand.domain.utilities.ReversibleUUIDv7;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de integração de concorrência real: dispara dois cancelamentos simultâneos sobre a mesma
 * autorização em threads distintas, verificando empiricamente se o lock otimista via @Version
 * dispara OptimisticLockException na segunda transação. Ponto central do design D5/D1 de
 * `integridade-fluxo-escrita` — sem esta validação real (não mockada), não há evidência de que a
 * correção funciona.
 *
 * Roda contra o PostgreSQL 18 local (pré-requisito declarado do build, "sem fallback H2") num
 * schema dedicado, criado e destruído por esta própria classe. Antes usava Testcontainers, que
 * neste projeto não consegue falar com builds recentes do Docker Desktop — a API Java falha com
 * HTTP 400 em {@code /info} mesmo com o CLI funcionando, e a classe inteira era pulada. Um teste
 * de concorrência que nunca executa não é evidência de nada: foi sob essa cobertura ausente que o
 * defeito corrigido por {@code corrigir-expurgo-merge-version} sobreviveu. Ver
 * {@link PostgresLocalDisponivelCondition}.
 */
@SpringBootTest
@ExtendWith(PostgresLocalDisponivelCondition.class)
@DisplayName("Teste de integração: concorrência otimista no cancelamento")
class ConcorrenciaOptimisticaIntegrationTest {

    /** Schema isolado: nada aqui toca a tabela `autorizacoes` real do banco de desenvolvimento. */
    private static final String SCHEMA = "test_concorrencia_otimista";

    private static final int PARTICAO_EXPURGO_HOJE =
            ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(LocalDate.now());

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> PostgresLocalDisponivelCondition.urlBase() + "?currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> PostgresLocalDisponivelCondition.USUARIO);
        registry.add("spring.datasource.password", () -> PostgresLocalDisponivelCondition.SENHA);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @BeforeAll
    static void criarSchemaIsolado() throws Exception {
        try (Connection conn = DriverManager.getConnection(PostgresLocalDisponivelCondition.urlBase(),
                     PostgresLocalDisponivelCondition.USUARIO, PostgresLocalDisponivelCondition.SENHA);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE;");
            stmt.execute("CREATE SCHEMA " + SCHEMA + ";");
            stmt.execute("SET search_path TO " + SCHEMA + ";");
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
                    CONSTRAINT pk_autorizacoees PRIMARY KEY (id_autorizacao, id_particao_conta)
                ) PARTITION BY LIST (id_particao_conta);
                """);
            // DEFAULT absorve a partição quente (derivada do hash da conta, imprevisível); a de
            // expurgo é explícita, para que a transferência seja movimentação real entre partições.
            stmt.execute("CREATE TABLE autorizacoes_default PARTITION OF autorizacoes DEFAULT;");
            stmt.execute("CREATE TABLE autorizacoes_pe%d PARTITION OF autorizacoes FOR VALUES IN (%d);"
                    .formatted(PARTICAO_EXPURGO_HOJE, PARTICAO_EXPURGO_HOJE));
            // Espelha a migration v1.0.4: unicidade da chave de negócio só nas partições quentes.
            stmt.execute("""
                CREATE UNIQUE INDEX uk_autorizacao_empresa_ativa
                    ON autorizacoes (id_particao_conta, id_autorizacao_empresa)
                    WHERE id_particao_conta < 900;
                """);
        }
    }

    @AfterAll
    static void removerSchemaIsolado() throws Exception {
        try (Connection conn = DriverManager.getConnection(PostgresLocalDisponivelCondition.urlBase(),
                     PostgresLocalDisponivelCondition.USUARIO, PostgresLocalDisponivelCondition.SENHA);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE;");
        }
    }

    @Autowired
    private AutorizacaoRepository repository;

    @Autowired
    private CancelarAutorizacaoUseCase cancelarUseCase;

    @Test
    @DisplayName("Dois cancelamentos concorrentes: exatamente um vence, o outro falha com OptimisticLockException")
    void doisCancelamentosConcorrentes_ExatamenteUmVence() throws InterruptedException {
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

        long quantidadeQueVenceu = Stream.of(primeiroErro.get(), segundoErro.get())
                .filter(Objects::isNull)
                .count();
        long quantidadeQueFalhouPorConcorrencia = Stream.of(primeiroErro.get(), segundoErro.get())
                .filter(this::causaContemFalhaDeConcorrencia)
                .count();

        // Antes da mudanca `corrigir-expurgo-merge-version`, esta classe tolerava "as duas podem
        // falhar" — resultado registrado como validado empiricamente na spec de concorrencia. Nao
        // era concorrencia: era a transacao falhando contra si mesma no `merge` de instancia
        // detached, com ou sem disputa. Corrigido o defeito, exatamente uma tem de vencer.
        assertEquals(1, quantidadeQueVenceu,
                "Exatamente uma das transacoes concorrentes deve ser confirmada. "
                        + "Primeiro erro: " + primeiroErro.get() + ", Segundo erro: " + segundoErro.get());
        assertEquals(1, quantidadeQueFalhouPorConcorrencia,
                "A transacao perdedora deve falhar por conflito de concorrencia, nao por outro motivo. "
                        + "Primeiro erro: " + primeiroErro.get() + ", Segundo erro: " + segundoErro.get());
    }

    /**
     * Aceita qualquer {@link ConcurrencyFailureException} — e não apenas a variante otimista —
     * porque a movimentação de partição muda a *forma* do conflito: quando a transação vencedora
     * move a linha para outra partição, o PostgreSQL não consegue seguir a cadeia de atualização
     * e devolve {@code "tuple to be locked was already moved to another partition due to
     * concurrent update"} (SQLSTATE 40001), que o Spring traduz para
     * {@code CannotAcquireLockException}, irmã de {@code ObjectOptimisticLockingFailureException}
     * sob {@code ConcurrencyFailureException}. Ambas significam a mesma coisa para o contrato da
     * API: conflito real entre chamadores, resposta 409.
     */
    private boolean causaContemFalhaDeConcorrencia(Throwable t) {
        while (t != null) {
            if (t instanceof ConcurrencyFailureException
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
