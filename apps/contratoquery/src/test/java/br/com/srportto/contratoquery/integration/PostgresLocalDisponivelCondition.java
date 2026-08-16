package br.com.srportto.contratoquery.integration;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.sql.DriverManager;

/**
 * Desabilita a classe anotada de forma visível no Surefire ("Skipped") quando o PostgreSQL local
 * não está acessível — diferente de {@code Assumptions.assumeTrue}, que reporta "Tests run: 0".
 * Gêmeo (duplicado de propósito) do mesmo arquivo em `contratocommand`. Senha vem só de
 * {@code DB_PASSWORD}, sem default, mesma postura do resto do repo contra credencial embutida.
 */
public class PostgresLocalDisponivelCondition implements ExecutionCondition {

    static final String HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    static final String PORTA = System.getenv().getOrDefault("DB_PORT", "5432");
    static final String BANCO = System.getenv().getOrDefault("DB_NAME", "db-csp-postgres");
    // Público: ConsultaCascataIntegrationTest vive em infrastructure.persistence (precisa enxergar
    // o repositório Spring Data package-private), fora do pacote desta classe.
    public static final String USUARIO = System.getenv().getOrDefault("DB_USER_NAME", "docker");
    public static final String SENHA = System.getenv("DB_PASSWORD");

    public static String urlBase() {
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
