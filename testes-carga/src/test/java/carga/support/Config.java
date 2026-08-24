package carga.support;

/**
 * Configuracao central dos cenarios de carga -- URLs base e limiares dos kill switches.
 * Tudo lido de variavel de ambiente com default apontando para o ambiente local
 * (docker-compose da raiz do monorepo). Nao ha configuracao de ambiente prod-like
 * (change testes-de-carga-tps, proposal.md: execucao so local).
 */
public final class Config {

    private Config() {
    }

    public static String contratocommandBaseUrl() {
        return System.getenv().getOrDefault("CONTRATOCOMMAND_BASE_URL", "http://localhost:8080");
    }

    public static String contratoqueryBaseUrl() {
        return System.getenv().getOrDefault("CONTRATOQUERY_BASE_URL", "http://localhost:8081");
    }

    public static String flociEndpoint() {
        return System.getenv().getOrDefault("FLOCI_ENDPOINT", "http://localhost:4566");
    }

    public static String kafkaBootstrapServers() {
        return System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:19092");
    }

    public static String idUnicoContaContratanteTeste() {
        // Conta fixa dedicada a teste de carga -- nao e uma conta de producao nem compartilha
        // id com massa sintetica de outro proposito (gerar-massa-sintetica-*.sql).
        return System.getenv().getOrDefault(
                "LOADTEST_CONTA_ID", "10000000-0000-0000-0000-000000000001");
    }

    // --- Kill switches (design.md D2) -- ponto de partida, sujeitos a calibracao (tasks.md 8.3) ---

    /** Nivel aplicacao: conexoes pendentes no pool Hikari sustentadas acima de zero (D1). */
    public static double limiteHikariConnectionsPending() {
        return Double.parseDouble(System.getenv().getOrDefault("LOADTEST_LIMITE_HIKARI_PENDING", "0"));
    }

    /** Nivel aplicacao: taxa de erro REAL (D3 -- ja excluidos 409 e CannotAcquireLockException). */
    public static double limiteTaxaErroReal() {
        return Double.parseDouble(System.getenv().getOrDefault("LOADTEST_LIMITE_TAXA_ERRO_REAL", "0.01"));
    }

    /** Nivel aplicacao: p99 de latencia em milissegundos. */
    public static int limiteP99Ms() {
        return Integer.parseInt(System.getenv().getOrDefault("LOADTEST_LIMITE_P99_MS", "2000"));
    }

    /** Nivel fila/lag: profundidade maxima aceitavel de fila SQS antes de abortar. */
    public static long limiteProfundidadeFilaSqs() {
        return Long.parseLong(System.getenv().getOrDefault("LOADTEST_LIMITE_FILA_SQS", "1000"));
    }

    /** Nivel fila/lag: lag maximo aceitavel do consumer group Kafka antes de abortar. */
    public static long limiteLagKafka() {
        return Long.parseLong(System.getenv().getOrDefault("LOADTEST_LIMITE_LAG_KAFKA", "1000"));
    }
}
