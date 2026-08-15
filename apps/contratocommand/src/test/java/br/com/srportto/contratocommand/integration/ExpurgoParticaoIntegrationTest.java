package br.com.srportto.contratocommand.integration;

import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.port.in.CancelarAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.port.in.DecidirAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.port.in.DecidirAutorizacaoCommand;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testa a transferência para a partição de expurgo contra PostgreSQL real, com partições físicas
 * distintas (uma DEFAULT esconderia a movimentação). Existe porque o defeito corrigido por
 * `corrigir-expurgo-merge-version` era invisível a teste mockado: `ExpurgoAutorizacaoServiceTest`
 * só verificava a ordem das chamadas, não a decisão que o Hibernate toma dentro do `save` com
 * `@Version` num banco real.
 *
 * Roda em schema dedicado (não via Testcontainers, que não fala com Docker Desktop recente aqui).
 * Ver {@link PostgresLocalDisponivelCondition}.
 */
@SpringBootTest
@ExtendWith(PostgresLocalDisponivelCondition.class)
@DisplayName("Teste de integração: transferência para a partição de expurgo")
class ExpurgoParticaoIntegrationTest {

    // Partições fixas (schema criado antes de existirem contas); duas contas em partições
    // quentes distintas convergem para a mesma partição de expurgo.
    private static final int PARTICAO_QUENTE = 5;
    private static final int OUTRA_PARTICAO_QUENTE = 7;

    private static final int PARTICAO_EXPURGO_HOJE =
            ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(LocalDate.now());

    /** Schema isolado: nada aqui toca a tabela `autorizacoes` real do banco de desenvolvimento. */
    private static final String SCHEMA = "test_expurgo_particao";

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
            // Partições físicas separadas tornam a movimentação observável.
            for (int particao : new int[] {PARTICAO_QUENTE, OUTRA_PARTICAO_QUENTE}) {
                stmt.execute("CREATE TABLE autorizacoes_pa%d PARTITION OF autorizacoes FOR VALUES IN (%d);"
                        .formatted(particao, particao));
            }
            stmt.execute("CREATE TABLE autorizacoes_pe%d PARTITION OF autorizacoes FOR VALUES IN (%d);"
                    .formatted(PARTICAO_EXPURGO_HOJE, PARTICAO_EXPURGO_HOJE));
            // Espelha a unicidade de negócio das migrations — manter atualizado junto com elas.
            stmt.execute(unicidadeChaveDeNegocio());
        }
    }

    /** Espelho da unicidade de negócio das migrations em {@code infra/local/postgres/migrations/}. */
    private static String unicidadeChaveDeNegocio() {
        // Índice parcial (v1.0.4): unicidade só nas partições quentes. Na de expurgo,
        // id_particao_conta é o balde semanal — impor a chave ali colidiria contas distintas.
        return """
            CREATE UNIQUE INDEX uk_autorizacao_empresa_ativa
                ON autorizacoes (id_particao_conta, id_autorizacao_empresa)
                WHERE id_particao_conta < 900;
            """;
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
    private DecidirAutorizacaoUseCase decidirUseCase;

    @Autowired
    private CancelarAutorizacaoUseCase cancelarUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("EXPIRAR move a autorizacao para a particao de expurgo, sem conflito")
    void expirar_MoveParaParticaoDeExpurgo() {
        Autorizacao autorizacao = persistirRecebida("emp-expirar-" + UUID.randomUUID());
        UUID id = autorizacao.getIdAutorizacao().getIdAutorizacao();

        assertDoesNotThrow(() -> decidirUseCase.execute(decisao(id, "EXPIRAR")),
                "Expiracao isolada, sem nenhum chamador concorrente, nao pode falhar por conflito");

        assertAll(
                () -> assertEquals(0, contarEm(PARTICAO_QUENTE, id),
                        "A linha deveria ter saido da particao de origem"),
                () -> assertEquals(1, contarEm(PARTICAO_EXPURGO_HOJE, id),
                        "A linha deveria estar na particao de expurgo do dia"),
                () -> assertEquals((int) StatusAutorizacao.REJEITADA.getStatusAutorizacao(), statusDe(id),
                        "O status persistido deveria ser REJEITADA"),
                () -> assertEquals("REJEITADA_SISTEMA_TIMEOUT_J1", motivoDe(id)));
    }

    @Test
    @DisplayName("REJEITAR move a autorizacao para a particao de expurgo, sem conflito")
    void rejeitar_MoveParaParticaoDeExpurgo() {
        Autorizacao autorizacao = persistirRecebida("emp-rejeitar-" + UUID.randomUUID());
        UUID id = autorizacao.getIdAutorizacao().getIdAutorizacao();

        assertDoesNotThrow(() -> decidirUseCase.execute(decisao(id, "REJEITAR")));

        assertAll(
                () -> assertEquals(0, contarEm(PARTICAO_QUENTE, id)),
                () -> assertEquals(1, contarEm(PARTICAO_EXPURGO_HOJE, id)),
                () -> assertEquals("REJEITADA_PAGADOR", motivoDe(id)));
    }

    @Test
    @DisplayName("Cancelamento move a autorizacao para a particao de expurgo, sem conflito")
    void cancelar_MoveParaParticaoDeExpurgo() {
        // TransicaoStatusValida só permite cancelar a partir de ATIVA.
        Autorizacao autorizacao = persistir("emp-cancelar-" + UUID.randomUUID(), PARTICAO_QUENTE,
                StatusAutorizacao.ATIVA, "AUTORIZACAO_ACEITA_POR_TODOS");
        UUID id = autorizacao.getIdAutorizacao().getIdAutorizacao();

        assertDoesNotThrow(() -> cancelarUseCase.execute(
                TestFixtures.cancelarContext(id.toString(), TipoProduto.PIX_AUTO)));

        assertAll(
                () -> assertEquals(0, contarEm(PARTICAO_QUENTE, id)),
                () -> assertEquals(1, contarEm(PARTICAO_EXPURGO_HOJE, id)),
                () -> assertEquals((int) StatusAutorizacao.CANCELADA.getStatusAutorizacao(), statusDe(id)));
    }

    @Test
    @DisplayName("A transferencia incrementa a versao do lock otimista")
    void transferencia_IncrementaVersao() {
        Autorizacao autorizacao = persistirRecebida("emp-versao-" + UUID.randomUUID());
        UUID id = autorizacao.getIdAutorizacao().getIdAutorizacao();
        long versaoAntes = versaoDe(id);

        decidirUseCase.execute(decisao(id, "EXPIRAR"));

        assertEquals(versaoAntes + 1, versaoDe(id),
                "A escrita das colunas de negocio deve passar pelo dirty-check versionado do JPA");
    }

    @Test
    @DisplayName("Chave de empresa repetida entre contas distintas nao impede o expurgo (D3a)")
    void chaveDeEmpresaRepetidaEntreContas_NaoImpedeExpurgo() {
        // Contas em partições quentes distintas convergem para a mesma partição de expurgo ao
        // expirarem na mesma semana, onde id_particao_conta deixa de identificar a conta.
        String chaveCompartilhada = "pedido-compartilhado-" + UUID.randomUUID();
        Autorizacao primeira = persistirRecebida(chaveCompartilhada, PARTICAO_QUENTE);
        Autorizacao segunda = persistirRecebida(chaveCompartilhada, OUTRA_PARTICAO_QUENTE);
        UUID idPrimeira = primeira.getIdAutorizacao().getIdAutorizacao();
        UUID idSegunda = segunda.getIdAutorizacao().getIdAutorizacao();

        decidirUseCase.execute(decisao(idPrimeira, "EXPIRAR"));

        assertDoesNotThrow(() -> decidirUseCase.execute(decisao(idSegunda, "EXPIRAR")),
                "Duas contas distintas com a mesma chave de empresa devem coexistir na particao de expurgo");

        assertAll(
                () -> assertEquals(1, contarEm(PARTICAO_EXPURGO_HOJE, idPrimeira)),
                () -> assertEquals(1, contarEm(PARTICAO_EXPURGO_HOJE, idSegunda)));
    }

    private DecidirAutorizacaoCommand decisao(UUID idAutorizacao, String acao) {
        return DecidirAutorizacaoCommand.doRequest(idAutorizacao.toString(), TipoProduto.PIX_AUTO,
                acao, "C1", UUID.randomUUID());
    }

    private int contarEm(int particao, UUID idAutorizacao) {
        String tabela = particao < 900 ? "autorizacoes_pa" + particao : "autorizacoes_pe" + particao;
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + tabela + " WHERE id_autorizacao = ?", Integer.class, idAutorizacao);
    }

    private int statusDe(UUID idAutorizacao) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM autorizacoes WHERE id_autorizacao = ?", Integer.class, idAutorizacao);
    }

    private String motivoDe(UUID idAutorizacao) {
        return jdbcTemplate.queryForObject(
                "SELECT motivo_status FROM autorizacoes WHERE id_autorizacao = ?", String.class, idAutorizacao);
    }

    private long versaoDe(UUID idAutorizacao) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM autorizacoes WHERE id_autorizacao = ?", Long.class, idAutorizacao);
    }

    private Autorizacao persistirRecebida(String idAutorizacaoEmpresa) {
        return persistirRecebida(idAutorizacaoEmpresa, PARTICAO_QUENTE);
    }

    private Autorizacao persistirRecebida(String idAutorizacaoEmpresa, int particaoQuente) {
        return persistir(idAutorizacaoEmpresa, particaoQuente, StatusAutorizacao.RECEBIDA, "RECEPCAO_SPI_J1");
    }

    /** Sorteia a conta até o hash cair na partição informada (schema já existe antes da conta). */
    private Autorizacao persistir(String idAutorizacaoEmpresa, int particaoQuente,
            StatusAutorizacao status, String motivoStatus) {
        UUID idUnicoConta = contaNaParticao(particaoQuente);
        UUID idAutorizacao = ReversibleUUIDv7.generate(particaoQuente);

        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(new IdAutorizacao(idAutorizacao, particaoQuente));
        aut.setIdAutorizacaoEmpresa(idAutorizacaoEmpresa);
        aut.setTipoProduto(TipoProduto.PIX_AUTO);
        aut.setTipoJornada(TipoJornadaAutorizacao.SPI_J1);
        aut.setStatus((int) status.getStatusAutorizacao());
        aut.setMotivoStatus(motivoStatus);
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
        // version nulo de propósito: é o que faz o Spring Data escolher persist em vez de merge.

        return repository.save(aut);
    }

    private UUID contaNaParticao(int particaoAlvo) {
        UUID candidata;
        do {
            candidata = UUID.randomUUID();
        } while (IdContaUUIDPartitionDistributor.getPartitionFast(candidata) != particaoAlvo);
        return candidata;
    }
}
