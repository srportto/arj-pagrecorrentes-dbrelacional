package br.com.srportto.contratocommand.integration;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Desabilita a classe de teste anotada, de forma visível no relatório do Surefire ("Skipped"),
 * quando o Docker não está acessível pela API Java do Testcontainers — diferente de
 * {@code Assumptions.assumeTrue} num {@code @BeforeAll} de {@code @SpringBootTest}, que aborta a
 * classe inteira e é reportado como "Tests run: 0", indistinguível de uma classe sem testes.
 */
public class DockerDisponivelCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        boolean disponivel;
        String motivo;
        try {
            disponivel = DockerClientFactory.instance().isDockerAvailable();
            motivo = disponivel ? "Docker disponível" : "Docker não acessível pela API Java do Testcontainers";
        } catch (Exception e) {
            disponivel = false;
            motivo = "Falha ao checar disponibilidade do Docker: " + e.getMessage();
        }
        if (!disponivel) {
            return ConditionEvaluationResult.disabled(
                    "PULADO (não é falha): " + motivo + " — o resultado empírico deste teste já foi "
                            + "validado manualmente e está documentado em design.md/tasks.md de "
                            + "integridade-fluxo-escrita. Rode localmente com Docker acessível para "
                            + "confirmar de novo.");
        }
        return ConditionEvaluationResult.enabled("Docker disponível");
    }
}
