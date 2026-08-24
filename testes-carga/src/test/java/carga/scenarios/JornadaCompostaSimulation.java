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
 * Cenario de jornada composta (change testes-de-carga-tps) -- encadeia criacao e decisao no
 * contratocommand, disparando o pipeline assincrono completo (SNS -> SQS ->
 * autorizacaostatus-producer -> Kafka -> eventos-consumer, e SNS -> SQS filtrada ->
 * temporiza-autorizacao -> Valkey). Este cenario NAO chama o pipeline assincrono diretamente
 * -- ele so gera o evento; a observacao de lag/profundidade de fila e feita por fora
 * (scripts/fila-lag-monitor.sh), rodando em paralelo a esta simulacao.
 *
 * <p>Estrategia de carga: patamar fixo sustentado, NAO ramp-up (design.md D4) -- revela se o
 * lag diverge ao longo do tempo sob carga constante; um ramp-up mascararia esse efeito porque
 * a carga muda antes do lag ter tempo de estabilizar ou divergir.
 *
 * <pre>
 *   mvn io.gatling:gatling-maven-plugin:test -Dgatling.simulationClass=carga.scenarios.JornadaCompostaSimulation
 * </pre>
 */
public class JornadaCompostaSimulation extends Simulation {

    private final HttpProtocolBuilder protocol = http
            .baseUrl(Config.contratocommandBaseUrl())
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    private final ScenarioBuilder cenario = scenario("Jornada composta: criar -> decidir (dispara pipeline assincrono)")
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
                                      "descricao": "teste de carga - jornada composta",
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
                            http("PATCH /decisao (aprovar -- dispara evento ATIVACAO)")
                                    .patch("/api/autorizacoes/#{idAutorizacao}/decisao")
                                    .header("tipoProduto", "PIX_AUTO")
                                    .body(StringBody("""
                                            {"acao": "APROVAR", "codigoCanalDecisao": "01"}
                                            """))
                                    .check(status().saveAs("statusDecisao"))
                    ).exec(session -> {
                        ErroClassificador.classificarGenerico(session.getInt("statusDecisao"));
                        return session;
                    })
            );

    @Override
    public void before() {
        System.out.println(
                "Iniciando jornada composta -- patamar fixo, observar lag/profundidade de fila externamente " +
                        "(scripts/fila-lag-monitor.sh) durante toda a execucao.");
    }

    @Override
    public void after() {
        System.out.println("Resumo de classificacao de erro (D3): " + ErroClassificador.resumo());
    }

    {
        setUp(
                cenario.injectOpen(
                        // Patamar fixo sustentado (D4) -- nao crescente. Baseline atual: 30/s
                        // por 8 min. Mesmo neste patamar (6x um teto inicial de 5/s que nao
                        // gerou lag algum), fila SQS e lag Kafka permaneceram em 0 durante toda
                        // a execucao -- pipeline assincrono com headroom alem de 60 req/s
                        // (ver testes-carga/relatorios/RESUMO-baseline-2026-08-23.md).
                        constantUsersPerSec(30).during(Duration.ofMinutes(8))
                )
        ).protocols(protocol);
    }
}
