package br.com.srportto.contratocommand.integration;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.sql.DriverManager;

/**
 * Desabilita a classe de teste anotada de forma visível no Surefire ("Skipped") quando o
 * PostgreSQL local não está acessível — diferente de {@code Assumptions.assumeTrue} num
 * {@code @BeforeAll}, que reporta "Tests run: 0", indistinguível de classe sem testes.
 *
 * Usa instância local em vez de Testcontainers porque a versão usada aqui não fala com builds
 * recentes do Docker Desktop (HTTP 400 em {@code /info}), pulando sempre a classe — foi sob essa
 * cobertura ausente que o defeito de {@code corrigir-expurgo-merge-version} sobreviveu.
 *
 * Senha vem só de {@code DB_PASSWORD}, sem default, como em {@code docker-compose.yml}.
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
