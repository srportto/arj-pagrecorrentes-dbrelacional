package br.com.srportto.contratoquery.integration;

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
 * Gêmeo do mesmo arquivo em `arj-contratocommand`. Duplicado de propósito: as duas apps não
 * compartilham módulo de teste, e um módulo comum só para esta classe custaria mais do que
 * resolve.
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
