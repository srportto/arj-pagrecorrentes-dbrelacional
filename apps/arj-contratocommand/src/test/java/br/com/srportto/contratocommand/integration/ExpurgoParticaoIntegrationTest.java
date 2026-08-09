package br.com.srportto.contratocommand.integration;

import br.com.srportto.contratocommand.application.AutorizacaoRepository;
import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.cancelamento.CancelarAutorizacaoUseCase;
import br.com.srportto.contratocommand.application.decisao.DecidirAutorizacaoUseCase;
import br.com.srportto.contratocommand.application.decisao.DecisaoContext;
import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.utilities.ControleExpurgoAutorizacao;
import br.com.srportto.contratocommand.domain.utilities.IdContaUUIDPartitionDistributor;
import br.com.srportto.contratocommand.domain.utilities.ReversibleUUIDv7;
import br.com.srportto.contratocommand.entrypoint.contratosrest.DecisaoAutorizacaoRequest;
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
 * Teste de integração da transferência para a partição de expurgo, contra PostgreSQL real e com
 * partições físicas distintas — não com uma partição DEFAULT que absorveria origem e destino e
 * esconderia a movimentação.
 *
 * Existe porque o defeito corrigido pela mudança `corrigir-expurgo-merge-version` era invisível a
 * teste mockado: o `ExpurgoAutorizacaoServiceTest` verificava a *ordem* das chamadas ao
 * repositório (`deleteById` → `flush` → `detach` → `save`) e ficou verde durante todo o período em
 * que toda expiração e todo cancelamento com troca de partição respondiam `409`. O defeito não
 * estava na sequência de chamadas: estava na decisão que o Hibernate toma dentro do `save`, diante
 * de um banco real, quando a entidade tem `@Version`.
 *
 * Roda contra o PostgreSQL 18 local (pré-requisito declarado do build, "sem fallback H2") num
 * schema dedicado, criado e destruído por esta própria classe — e não via Testcontainers, que
 * neste projeto não consegue falar com builds recentes do Docker Desktop e faz a classe inteira
 * ser pulada. Ver {@link PostgresLocalDisponivelCondition}.
 */
@SpringBootTest
@ExtendWith(PostgresLocalDisponivelCondition.class)
@DisplayName("Teste de integração: transferência para a partição de expurgo")
class ExpurgoParticaoIntegrationTest {

    /**
     * Partições quentes fixas, para o schema poder ser criado antes de conhecer as contas
     * contratantes. Duas são necessárias: contas distintas vivem em partições quentes distintas,
     * e é justamente esse par que converge para a mesma partição de expurgo.
     */
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
            // Partições físicas separadas: é o que torna a movimentação observável. Com uma
            // partição DEFAULT única, origem e destino cairiam na mesma tabela física.
            for (int particao : new int[] {PARTICAO_QUENTE, OUTRA_PARTICAO_QUENTE}) {
                stmt.execute("CREATE TABLE autorizacoes_pa%d PARTITION OF autorizacoes FOR VALUES IN (%d);"
                        .formatted(particao, particao));
            }
            stmt.execute("CREATE TABLE autorizacoes_pe%d PARTITION OF autorizacoes FOR VALUES IN (%d);"
                    .formatted(PARTICAO_EXPURGO_HOJE, PARTICAO_EXPURGO_HOJE));
            // Unicidade da chave de negócio: espelha o que as migrations produzem hoje. Deve ser
            // atualizada junto com elas — é o que mantém este teste honesto sobre o schema real.
            stmt.execute(unicidadeChaveDeNegocio());
        }
    }

    /**
     * Espelho da unicidade da chave de negócio produzida pelas migrations em
     * {@code infra/local/postgres/migrations/}. Precisa ser atualizado junto com elas — é o que
     * mantém este teste honesto sobre o schema real, em vez de testar um schema idealizado.
     */
    private static String unicidadeChaveDeNegocio() {
        // v1.0.4 — índice único PARCIAL: a unicidade da chave de negócio é regra sobre
        // autorizações ativas, restrita às partições quentes. Nas de expurgo,
        // `id_particao_conta` é o balde semanal, e impor a chave ali faria autorizações de
        // contas distintas colidirem.
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
        // Cancelamento só é permitido a partir de ATIVA (rule TransicaoStatusValida).
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
        // Contas distintas vivem em partições quentes distintas — é lá que a unicidade vale.
        // Ao expirarem na mesma semana, ambas convergem para a MESMA partição de expurgo, onde
        // `id_particao_conta` já não representa a conta e a chave (partição, empresa) colide.
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

    private DecisaoContext decisao(UUID idAutorizacao, String acao) {
        return DecisaoContext.doRequest(idAutorizacao.toString(), TipoProduto.PIX_AUTO,
                new DecisaoAutorizacaoRequest(acao, "C1", UUID.randomUUID()));
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

    /**
     * Persiste uma autorização na partição quente informada. A conta contratante é sorteada até
     * que seu hash caia nessa partição, para que o schema possa ser criado antes de ela existir.
     */
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
        // version fica nulo de propósito: é assim que o Spring Data decide entre persist e merge.

        return repository.saveAndFlush(aut);
    }

    private UUID contaNaParticao(int particaoAlvo) {
        UUID candidata;
        do {
            candidata = UUID.randomUUID();
        } while (IdContaUUIDPartitionDistributor.getPartitionFast(candidata) != particaoAlvo);
        return candidata;
    }
}
