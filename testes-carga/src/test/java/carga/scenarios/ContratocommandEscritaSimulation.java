package carga.scenarios;

import carga.support.Config;
import carga.support.ErroClassificador;
import carga.support.MassaTeste;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Cenario isolado de escrita (change testes-de-carga-tps) -- mede TPS do contratocommand
 * sozinho: criacao (PIX_AUTO, nasce RECEBIDA) -> decisao (APROVAR) -> cancelamento, tudo na
 * mesma cadeia por usuario virtual. Nao toca contratoquery nem o pipeline assincrono
 * diretamente (Requirement "Cenario isolado de escrita mede TPS do contratocommand").
 *
 * <p>Estrategia de carga: ramp-up gradual (design.md D4) -- revela o "joelho da curva" antes
 * de decidir por onde a taxa de erro real (D3) ultrapassa o limiar de abort.
 *
 * <p>Execucao (baseline, sem recalibrar tetos -- ver proposal.md):
 * <pre>
 *   mvn io.gatling:gatling-maven-plugin:test -Dgatling.simulationClass=carga.scenarios.ContratocommandEscritaSimulation
 * </pre>
 * O kill switch de nivel aplicacao/fila (design.md D2) roda por fora, via
 * scripts/kill-switch-monitor.sh apontado para o PID deste processo (ver scripts/rodar-cenario.sh).
 */
public class ContratocommandEscritaSimulation extends Simulation {

    private final HttpProtocolBuilder protocol = http
            .baseUrl(Config.contratocommandBaseUrl())
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    private final ScenarioBuilder cenario = scenario("Escrita contratocommand: criar -> decidir -> cancelar")
            .exec(session -> session.set("idAutorizacaoEmpresa", MassaTeste.novoIdAutorizacaoEmpresa()))
            .exec(
                    http("POST /api/autorizacoes (criar PIX_AUTO)")
                            .post("/api/autorizacoes")
                            .header("tipoJornada", "SPI_J1")
                            .body(StringBody(session -> """
                                    {
                                      "tipoProduto": "PIX_AUTO",
                                      "valor": 100.00,
                                      "idAutorizacaoEmpresa": "%s",
                                      "frequencia": 1,
                                      "quantidadeDividasCiclo": 1,
                                      "indicadorUsoLimiteConta": 1,
                                      "codigoCanalContratacao": "01",
                                      "descricao": "teste de carga - contratocommand",
                                      "idUnicoContaContratante": "%s",
                                      "idPessoaPagadora": "10000000-0000-0000-0000-000000000002",
                                      "idPessoaDevedora": "10000000-0000-0000-0000-000000000003",
                                      "idPessoaRecebedora": "10000000-0000-0000-0000-000000000004"
                                    }
                                    """.formatted(
                                    session.getString("idAutorizacaoEmpresa"),
                                    Config.idUnicoContaContratanteTeste())))
                            .check(status().saveAs("statusCriacao"))
                            .check(jsonPath("$.idAutorizacao").optional().saveAs("idAutorizacao"))
            )
            .exec(session -> {
                ErroClassificador.classificarCriacao(session.getInt("statusCriacao"));
                return session;
            })
            .doIf(session -> session.contains("idAutorizacao") && session.getString("idAutorizacao") != null)
            .then(
                    exec(
                            http("PATCH /decisao (aprovar)")
                                    .patch("/api/autorizacoes/#{idAutorizacao}/decisao")
                                    .header("tipoProduto", "PIX_AUTO")
                                    .body(StringBody("""
                                            {"acao": "APROVAR", "codigoCanalDecisao": "01"}
                                            """))
                                    .check(status().saveAs("statusDecisao"))
                    ).exec(session -> {
                        ErroClassificador.classificarGenerico(session.getInt("statusDecisao"));
                        return session;
                    }).exec(
                            http("PATCH /cancelar")
                                    .patch("/api/autorizacoes/#{idAutorizacao}/cancelar")
                                    .header("tipoProduto", "PIX_AUTO")
                                    .body(StringBody("""
                                            {
                                              "codigoCanalCancelamento": "01",
                                              "idPessoaCancelamento": "10000000-0000-0000-0000-000000000002",
                                              "motivoCancelamento": "TESTE_DE_CARGA"
                                            }
                                            """))
                                    .check(status().saveAs("statusCancelamento"))
                    ).exec(session -> {
                        ErroClassificador.classificarGenerico(session.getInt("statusCancelamento"));
                        return session;
                    })
            );

    @Override
    public void before() {
        System.out.println("Iniciando cenario de escrita isolada (contratocommand) -- baseline, sem recalibrar tetos.");
    }

    @Override
    public void after() {
        System.out.println("Resumo de classificacao de erro (D3): " + ErroClassificador.resumo());
    }

    {
        setUp(
                cenario.injectOpen(
                        // Ramp-up gradual (D4). Um teto de 1->50/s (tentativa inicial) nao
                        // encontrou colapso -- 10->400/s e o baseline atual; mesmo assim, o
                        // teto real do contratocommand nao foi encontrado com um unico gerador
                        // de carga local (esbarra em esgotamento de porta efemera do cliente
                        // antes do servidor mostrar sinal real de colapso -- ver
                        // testes-carga/relatorios/RESUMO-baseline-2026-08-23.md).
                        rampUsersPerSec(10).to(400).during(Duration.ofMinutes(4))
                )
        ).protocols(protocol);
    }
}
