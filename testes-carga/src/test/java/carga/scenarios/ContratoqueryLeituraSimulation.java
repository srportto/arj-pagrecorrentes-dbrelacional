package carga.scenarios;

import carga.support.Config;
import carga.support.ErroClassificador;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Cenario isolado de leitura (change testes-de-carga-tps) -- mede TPS do contratoquery
 * sozinho: GET /api/autorizacoes (listagem por conta). Nao toca o contratocommand
 * (Requirement "Cenario isolado de leitura mede TPS do contratoquery").
 *
 * <p>Este endpoint ja e documentado (apps/contratoquery/CLAUDE.md, armadilha 8) como sensivel
 * a volume: filtra por conta, nao pela chave de particao, entao varre fisicamente as
 * particoes quentes a cada chamada. Esperado que o joelho da curva apareca mais cedo aqui do
 * que na escrita.
 *
 * <p>Estrategia de carga: ramp-up gradual (design.md D4).
 *
 * <pre>
 *   mvn io.gatling:gatling-maven-plugin:test -Dgatling.simulationClass=carga.scenarios.ContratoqueryLeituraSimulation
 * </pre>
 */
public class ContratoqueryLeituraSimulation extends Simulation {

    private final HttpProtocolBuilder protocol = http
            .baseUrl(Config.contratoqueryBaseUrl())
            .acceptHeader("application/json");

    private final ScenarioBuilder cenario = scenario("Leitura contratoquery: listar autorizacoes")
            .exec(
                    http("GET /api/autorizacoes")
                            .get("/api/autorizacoes")
                            .queryParam("idUnicoContaContratante", Config.idUnicoContaContratanteTeste())
                            .queryParam("tamanho", "20")
                            .check(status().saveAs("statusListagem"))
            )
            .exec(session -> {
                ErroClassificador.classificarGenerico(session.getInt("statusListagem"));
                return session;
            });

    @Override
    public void before() {
        System.out.println("Iniciando cenario de leitura isolada (contratoquery) -- baseline, sem recalibrar tetos.");
    }

    @Override
    public void after() {
        System.out.println("Resumo de classificacao de erro (D3): " + ErroClassificador.resumo());
    }

    {
        setUp(
                cenario.injectOpen(
                        // Baseline atual: ramp 10->400/s. REQUER massa sintetica representativa
                        // carregada antes (infra/local/postgres/gerar-massa-sintetica-representativa.sql,
                        // ~281 mil linhas, 889 particoes) -- contra banco vazio este teste nao
                        // exercita o custo real de scan documentado em
                        // apps/contratoquery/CLAUDE.md (armadilha 8) e o resultado nao e
                        // representativo (ver testes-carga/relatorios/RESUMO-baseline-2026-08-23.md,
                        // colapso real so aparece com a massa carregada: p99 salta de 18ms p/ 52s).
                        rampUsersPerSec(10).to(400).during(Duration.ofMinutes(4))
                )
        ).protocols(protocol);
    }
}
