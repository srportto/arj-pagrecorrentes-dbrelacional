package br.com.srportto.contratocommand.integration;

import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.port.in.CancelarAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.port.in.CancelarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.model.Autorizacao;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.infrastructure.persistence.ControleExpurgoAutorizacao;
import br.com.srportto.contratocommand.infrastructure.persistence.IdContaUUIDPartitionDistributor;
import br.com.srportto.contratocommand.infrastructure.persistence.ReversibleUUIDv7;
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
 * Dispara dois cancelamentos simultâneos sobre a mesma autorização em threads distintas e
 * verifica empiricamente se o lock otimista via @Version barra a segunda transação. Central ao
 * design D5/D1 de `integridade-fluxo-escrita` — sem validação real (não mockada) não há evidência
 * de que a correção funciona.
 *
 * Roda em schema dedicado (não via Testcontainers, que não fala com Docker Desktop recente aqui —
 * a classe inteira era pulada, e foi sob essa cobertura ausente que o defeito corrigido por
 * {@code corrigir-expurgo-merge-version} sobreviveu). Ver {@link PostgresLocalDisponivelCondition}.
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
                    motivo_status TEXT NOT NULL,
                    data_hora_inclusao timestamp NOT NULL,
                    data_hora_ultima_atlz timestamp NOT NULL,
                    data_inicio_vigencia DATE NOT NULL,
                    data_fim_vigencia DATE NOT NULL,
                    valor NUMERIC(17, 2) NOT NULL,
                    id_autorizacao_empresa TEXT NOT NULL,
                    valor_limite NUMERIC(17, 2),
                    frequencia INT NOT NULL CHECK (frequencia IN (1, 2, 3, 4)),
                    quantidade_dividas_ciclo INT NOT NULL,
                    indicador_uso_limite_conta INT NOT NULL,
                    indicador_tipo_mensageria INT NOT NULL,
                    codigo_canal_contratacao TEXT NOT NULL,
                    descricao TEXT,
                    id_unico_conta_contratante UUID NOT NULL,
                    id_pessoa_pagadora UUID NOT NULL,
                    id_pessoa_devedora UUID NOT NULL,
                    id_pessoa_recebedora UUID NOT NULL,
                    codigo_canal_cancelamento TEXT,
                    id_pessoa_cancelamento UUID,
                    data_hora_cancelamento timestamp,
                    motivo_cancelamento TEXT,
                    metadados JSON NOT NULL,
                    version BIGINT NOT NULL DEFAULT 0,
                    CONSTRAINT pk_autorizacoees PRIMARY KEY (id_autorizacao, id_particao_conta)
                ) PARTITION BY LIST (id_particao_conta);
                """);
            // DEFAULT absorve a partição quente (hash da conta, imprevisível); a de expurgo é
            // explícita, para a transferência ser movimentação real entre partições.
            stmt.execute("CREATE TABLE autorizacoes_default PARTITION OF autorizacoes DEFAULT;");
            stmt.execute("CREATE TABLE autorizacoes_pe%d PARTITION OF autorizacoes FOR VALUES IN (%d);"
                    .formatted(PARTICAO_EXPURGO_HOJE, PARTICAO_EXPURGO_HOJE));
            // Espelha a migration v1.0.4 (unicidade só nas partições quentes).
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
        repository.save(aut);
        UUID idAutorizacao = aut.getIdAutorizacao();

        CountDownLatch podeComecar = new CountDownLatch(1);
        CountDownLatch terminou = new CountDownLatch(2);
        AtomicReference<Exception> primeiroErro = new AtomicReference<>();
        AtomicReference<Exception> segundoErro = new AtomicReference<>();

        Thread thread1 = new Thread(() -> {
            try {
                podeComecar.await();
                CancelarAutorizacaoCommand ctx = TestFixtures.cancelarContext(idAutorizacao.toString(), TipoProduto.PIX_AUTO);
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
                CancelarAutorizacaoCommand ctx = TestFixtures.cancelarContext(idAutorizacao.toString(), TipoProduto.PIX_AUTO);
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

        // Antes de `corrigir-expurgo-merge-version`, as duas podiam falhar: nao era concorrencia,
        // era a transacao falhando contra si mesma no merge de instancia detached. Corrigido o
        // defeito, exatamente uma tem de vencer.
        assertEquals(1, quantidadeQueVenceu,
                "Exatamente uma das transacoes concorrentes deve ser confirmada. "
                        + "Primeiro erro: " + primeiroErro.get() + ", Segundo erro: " + segundoErro.get());
        assertEquals(1, quantidadeQueFalhouPorConcorrencia,
                "A transacao perdedora deve falhar por conflito de concorrencia, nao por outro motivo. "
                        + "Primeiro erro: " + primeiroErro.get() + ", Segundo erro: " + segundoErro.get());
    }

    /**
     * Aceita qualquer {@link ConcurrencyFailureException}, não só a variante otimista: mover a
     * linha de partição faz o Postgres devolver SQLSTATE 40001 ({@code CannotAcquireLockException}
     * no Spring), não erro de versão — mas ambas viram 409 no contrato da API.
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
        aut.setIdAutorizacao(idAutorizacao);
        aut.setIdParticaoConta(particao);
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
        // NÃO setar version: Spring Data usa version == null p/ decidir persist vs merge. Setar 0L
        // faria save() tentar UPDATE numa linha inexistente e falhar já no setup do teste.

        return aut;
    }
}
