package br.com.srportto.contratocommand.integration;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.sql.DriverManager;

/**
 * Desabilita a classe de teste anotada, de forma visível no relatório do Surefire ("Skipped"),
 * quando o PostgreSQL local exigido pelo build não está acessível — em vez de
 * {@code Assumptions.assumeTrue} num {@code @BeforeAll} de {@code @SpringBootTest}, que aborta a
 * classe inteira e é reportado como "Tests run: 0", indistinguível de uma classe sem testes.
 *
 * Por que a instância local e não Testcontainers: o Testcontainers 1.19.8 usado por este projeto
 * não consegue falar com builds recentes do Docker Desktop (a API Java falha com HTTP 400 em
 * {@code /info}, mesmo com o CLI funcionando), e as classes de teste de integração eram sempre
 * puladas. Um teste que nunca executa não protege nada — foi sob essa cobertura ausente que o
 * defeito corrigido por {@code corrigir-expurgo-merge-version} sobreviveu. O PostgreSQL 18 local
 * já é pré-requisito declarado do build ("sem fallback H2"), então usá-lo torna estes testes
 * efetivamente executáveis.
 *
 * A senha vem exclusivamente de {@code DB_PASSWORD}, sem valor padrão — mesma postura de
 * {@code apps/docker-compose.yml} e {@code infra/local/postgres/.env.example}, que se recusam a
 * embutir credencial.
 */
public class PostgresLocalDisponivelCondition implements ExecutionCondition {

    static final String HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    static final String PORTA = System.getenv().getOrDefault("DB_PORT", "5432");
    static final String BANCO = System.getenv().getOrDefault("DB_NAME", "db-csp-postgres");
    static final String USUARIO = System.getenv().getOrDefault("DB_USER_NAME", "docker");
    static final String SENHA = System.getenv("DB_PASSWORD");

    static String urlBase() {
        return "jdbc:postgresql://" + HOST + ":" + PORTA + "/" + BANCO;
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (SENHA == null || SENHA.isBlank()) {
            return desabilitado("variável de ambiente DB_PASSWORD não definida");
        }
        try (var ignored = DriverManager.getConnection(urlBase(), USUARIO, SENHA)) {
            return ConditionEvaluationResult.enabled("PostgreSQL local acessível");
        } catch (Exception e) {
            return desabilitado("não foi possível conectar em " + urlBase() + ": " + e.getMessage());
        }
    }

    private ConditionEvaluationResult desabilitado(String motivo) {
        return ConditionEvaluationResult.disabled(
                "PULADO (não é falha): " + motivo + " — suba o PostgreSQL local "
                        + "(infra/local/postgres/) e exporte DB_PASSWORD para executar este teste.");
    }
}
