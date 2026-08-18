package br.com.srportto.contratoquery.infrastructure.persistence;

import br.com.srportto.contratoquery.domain.enums.CampoOrdenacao;
import br.com.srportto.contratoquery.domain.enums.StatusAutorizacao;
import br.com.srportto.contratoquery.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratoquery.domain.enums.TipoProduto;
import br.com.srportto.contratoquery.domain.model.PaginaAutorizacoes;
import br.com.srportto.contratoquery.domain.port.out.AutorizacaoRepository;
import br.com.srportto.contratoquery.integration.PostgresLocalDisponivelCondition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de integração de {@link AutorizacaoJpaAdapter#listarPorConta} contra PostgreSQL real —
 * o método não tinha nenhuma cobertura além da porta mockada em
 * {@code ListarAutorizacoesServiceTest}, o que deixou passar a regressão do tipo do parâmetro
 * {@code statuses} (JPQL espera {@link StatusAutorizacao}, o adaptador enviava {@code Integer}).
 * Roda num schema dedicado, criado e destruído pela própria classe. Ver
 * {@link PostgresLocalDisponivelCondition}.
 */
@SpringBootTest
@ExtendWith(PostgresLocalDisponivelCondition.class)
@DisplayName("Teste de integração: listagem paginada por conta")
class ListarPorContaIntegrationTest {

    private static final int PARTICAO = 5;
    private static final String SCHEMA = "test_listar_por_conta";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> PostgresLocalDisponivelCondition.urlBase() + "?currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> PostgresLocalDisponivelCondition.USUARIO);
        registry.add("spring.datasource.password", () -> PostgresLocalDisponivelCondition.SENHA);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        // App é read-only por padrão, mas o setup grava fixtures via repositório.
        registry.add("spring.datasource.hikari.read-only", () -> "false");
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
            stmt.execute("CREATE TABLE autorizacoes_pa%d PARTITION OF autorizacoes FOR VALUES IN (%d);"
                    .formatted(PARTICAO, PARTICAO));
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
    private SpringDataAutorizacaoRepository springDataRepository;

    private UUID conta;

    @BeforeEach
    void limparEPopular() {
        springDataRepository.deleteAllInBatch();
        conta = UUID.randomUUID();
    }

    @Test
    @DisplayName("Sem filtro de status: lista todas as autorizacoes da conta")
    void semFiltroDeStatus_ListaTodas() {
        inserir(conta, StatusAutorizacao.RECEBIDA, "recepcao pendente");
        inserir(conta, StatusAutorizacao.ATIVA, "aceita por todos");
        inserir(UUID.randomUUID(), StatusAutorizacao.ATIVA, "de outra conta, nao deve aparecer");

        PaginaAutorizacoes pagina = repository.listarPorConta(
                conta, null, 0, 20, CampoOrdenacao.DATA_CRIACAO, true);

        assertEquals(2, pagina.totalElementos());
        assertEquals(2, pagina.conteudo().size());
    }

    @Test
    @DisplayName("Filtro com um status: so retorna autorizacoes naquele status")
    void filtroComUmStatus_RetornaSoAqueleStatus() {
        inserir(conta, StatusAutorizacao.RECEBIDA, "recepcao pendente");
        inserir(conta, StatusAutorizacao.ATIVA, "aceita por todos");

        PaginaAutorizacoes pagina = repository.listarPorConta(
                conta, List.of(StatusAutorizacao.ATIVA), 0, 20, CampoOrdenacao.DATA_CRIACAO, true);

        assertEquals(1, pagina.totalElementos());
        assertEquals(StatusAutorizacao.ATIVA, pagina.conteudo().get(0).getStatus());
    }

    @Test
    @DisplayName("Filtro com varios status: retorna a uniao dos status pedidos")
    void filtroComVariosStatus_RetornaUniao() {
        inserir(conta, StatusAutorizacao.RECEBIDA, "recepcao pendente");
        inserir(conta, StatusAutorizacao.ATIVA, "aceita por todos");
        inserir(conta, StatusAutorizacao.REJEITADA, "rejeitada pelo pagador");

        PaginaAutorizacoes pagina = repository.listarPorConta(
                conta, List.of(StatusAutorizacao.ATIVA, StatusAutorizacao.REJEITADA),
                0, 20, CampoOrdenacao.DATA_CRIACAO, true);

        assertEquals(2, pagina.totalElementos());
        assertTrue(pagina.conteudo().stream()
                .allMatch(a -> a.getStatus() == StatusAutorizacao.ATIVA || a.getStatus() == StatusAutorizacao.REJEITADA));
    }

    @ParameterizedTest
    @EnumSource(CampoOrdenacao.class)
    @DisplayName("Cada campo de ordenacao resolve para um caminho JPA valido, sem lancar excecao")
    void cadaCampoDeOrdenacao_ResolveSemErro(CampoOrdenacao campo) {
        inserir(conta, StatusAutorizacao.RECEBIDA, "recepcao pendente");
        inserir(conta, StatusAutorizacao.ATIVA, "aceita por todos");

        PaginaAutorizacoes pagina = repository.listarPorConta(
                conta, null, 0, 20, campo, true);

        assertEquals(2, pagina.totalElementos());
    }

    private void inserir(UUID idUnicoContaContratante, StatusAutorizacao status, String motivoStatus) {
        AutorizacaoJpaEntity a = new AutorizacaoJpaEntity();
        a.setIdAutorizacao(new IdAutorizacaoJpaEmbeddable(ReversibleUUIDv7.generate(PARTICAO), PARTICAO));
        a.setTipoProduto(TipoProduto.PIX_AUTO);
        a.setTipoJornada(TipoJornadaAutorizacao.SPI_J1);
        a.setStatus(status);
        a.setMotivoStatus(motivoStatus);
        a.setDataInicioVigencia(LocalDate.now());
        a.setDataFimVigencia(LocalDate.of(9999, 12, 31));
        a.setDataHoraInclusao(LocalDateTime.now());
        a.setDataHoraUltimaAtualizacao(LocalDateTime.now());
        a.setValorAutorizacao(new BigDecimal("1000.00"));
        a.setValorLimite(new BigDecimal("5000.00"));
        a.setIdAutorizacaoEmpresa("emp-" + UUID.randomUUID());
        a.setCodigoCanalContratacao("C1");
        a.setFrequenciaPagamento((short) 1);
        a.setQuantidadeDividasCiclo((short) 1);
        a.setIndicadorUsoLimiteConta((short) 0);
        a.setIndicadorTipoMensageria((short) 0);
        a.setIdUnicoContaContratante(idUnicoContaContratante);
        a.setIdPessoaPagadora(UUID.randomUUID());
        a.setIdPessoaDevedora(UUID.randomUUID());
        a.setIdPessoaRecebedora(UUID.randomUUID());
        a.setMetadados("{}");
        springDataRepository.saveAndFlush(a);
    }
}
