package br.com.srportto.contratoquery.integration;

import br.com.srportto.contratoquery.application.autorizacao.AutorizacaoRepository;
import br.com.srportto.contratoquery.application.autorizacao.ConsultarAutorizacaoService;
import br.com.srportto.contratoquery.domain.entities.Autorizacao;
import br.com.srportto.contratoquery.domain.entities.IdAutorizacao;
import br.com.srportto.contratoquery.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratoquery.domain.enums.TipoProduto;
import br.com.srportto.contratoquery.domain.utilities.ReversibleUUIDv7;
import br.com.srportto.contratoquery.entrypoint.contratosrest.AutorizacaoDetalheResponseDto;
import br.com.srportto.contratoquery.shared.exceptions.ApplicationException;
import br.com.srportto.contratoquery.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Teste de integração da cascata de localização do `GET /api/autorizacoes/{id}`, contra
 * PostgreSQL real com partições físicas distintas — teste mockado não pega o defeito corrigido
 * por `fallback-consulta-autorizacao-expurgada` (404 para autorização em estado terminal), pois
 * "funciona" contra chave composta mockada mesmo quando a linha já mudou de partição. Roda num
 * schema dedicado, criado e destruído pela própria classe. Ver {@link PostgresLocalDisponivelCondition}.
 */
@SpringBootTest
@ExtendWith(PostgresLocalDisponivelCondition.class)
@DisplayName("Teste de integração: cascata de localização na consulta por id")
class ConsultaCascataIntegrationTest {

    /** Partição embutida nos ids de teste. */
    private static final int PARTICAO_DO_ID = 5;

    /** Outra partição quente, usada no cenário de anomalia. */
    private static final int PARTICAO_INESPERADA = 7;

    /** Partições arbitrárias na faixa de expurgo (900–999). */
    private static final int PARTICAO_EXPURGO = 953;
    private static final int OUTRA_PARTICAO_EXPURGO = 954;

    private static final String SCHEMA = "test_consulta_cascata";

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
            for (int particao : new int[] {PARTICAO_DO_ID, PARTICAO_INESPERADA}) {
                stmt.execute("CREATE TABLE autorizacoes_pa%d PARTITION OF autorizacoes FOR VALUES IN (%d);"
                        .formatted(particao, particao));
            }
            for (int particao : new int[] {PARTICAO_EXPURGO, OUTRA_PARTICAO_EXPURGO}) {
                stmt.execute("CREATE TABLE autorizacoes_pe%d PARTITION OF autorizacoes FOR VALUES IN (%d);"
                        .formatted(particao, particao));
            }
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
    private ConsultarAutorizacaoService service;

    @Autowired
    private AutorizacaoRepository repository;

    @Test
    @DisplayName("N1: autorizacao ativa e encontrada na particao derivada do id")
    void nivel1_AutorizacaoAtiva() {
        UUID id = persistir(PARTICAO_DO_ID, PARTICAO_DO_ID, 4, "AUTORIZACAO_ACEITA_POR_TODOS");

        AutorizacaoDetalheResponseDto dto = service.consultarPorId(id);

        assertNotNull(dto);
        assertEquals(id, dto.getIdAutorizacao());
        assertEquals("ATIVA", dto.getStatus());
    }

    @Test
    @DisplayName("N2: autorizacao expurgada e encontrada na faixa de expurgo")
    void nivel2_AutorizacaoExpurgada() {
        // Id da partição 5, linha na 953 — o que o expurgo produz ao levar a autorização a estado terminal.
        UUID id = persistir(PARTICAO_DO_ID, PARTICAO_EXPURGO, 6, "REJEITADA_SISTEMA_TIMEOUT_J1");

        AutorizacaoDetalheResponseDto dto = service.consultarPorId(id);

        assertNotNull(dto, "Autorizacao em estado terminal precisa continuar consultavel por id");
        assertEquals(id, dto.getIdAutorizacao());
        assertEquals("REJEITADA", dto.getStatus());
    }

    @Test
    @DisplayName("N3: autorizacao em particao quente inesperada e encontrada (anomalia)")
    void nivel3_ParticaoQuenteInesperada() {
        // Nem na partição do id, nem no expurgo: viola o invariante de localização.
        UUID id = persistir(PARTICAO_DO_ID, PARTICAO_INESPERADA, 4, "AUTORIZACAO_ACEITA_POR_TODOS");

        AutorizacaoDetalheResponseDto dto = service.consultarPorId(id);

        assertNotNull(dto);
        assertEquals(id, dto.getIdAutorizacao());
    }

    @Test
    @DisplayName("Id inexistente resulta em 404 apos esgotar a cascata")
    void idInexistente_ResultaEmNaoEncontrado() {
        UUID id = ReversibleUUIDv7.generate(PARTICAO_DO_ID);

        assertThrows(ResourceNotFoundException.class, () -> service.consultarPorId(id));
    }

    @Test
    @DisplayName("Mesma autorizacao em duas particoes nao e resolvida silenciosamente")
    void duplicidadeEntreParticoes_NaoEscolheUma() {
        // Duplicidade precisa cair dentro do mesmo nível para ser observável — daí as duas cópias no expurgo.
        UUID id = ReversibleUUIDv7.generate(PARTICAO_DO_ID);
        inserir(id, PARTICAO_EXPURGO, 6, "REJEITADA_SISTEMA_TIMEOUT_J1");
        inserir(id, OUTRA_PARTICAO_EXPURGO, 5, "CANCELADA_PELO_PAGADOR");

        assertThrows(ApplicationException.class,
                () -> service.consultarPorId(id),
                "Mesma autorizacao em duas particoes e corrupcao, nao caso de desempate");
    }

    /** Persiste via repositório e devolve o id gerado para {@code particaoDoId}. */
    private UUID persistir(int particaoDoId, int particaoOndeReside, int status, String motivoStatus) {
        UUID id = ReversibleUUIDv7.generate(particaoDoId);
        inserir(id, particaoOndeReside, status, motivoStatus);
        return id;
    }

    /** Insere via JDBC puro — único jeito de colocar a linha numa partição diferente da embutida no id. */
    private void inserir(UUID id, int particao, int status, String motivoStatus) {
        Autorizacao a = new Autorizacao();
        a.setIdAutorizacao(new IdAutorizacao(id, particao));
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
        a.setIdUnicoContaContratante(UUID.randomUUID());
        a.setIdPessoaPagadora(UUID.randomUUID());
        a.setIdPessoaDevedora(UUID.randomUUID());
        a.setIdPessoaRecebedora(UUID.randomUUID());
        a.setMetadados("{}");
        repository.saveAndFlush(a);
    }
}
