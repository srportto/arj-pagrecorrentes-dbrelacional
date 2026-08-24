package carga.support;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Classificacao de erro em 3 categorias (change testes-de-carga-tps, design.md D3):
 * esperado-por-design (ex.: HTTP 409 de idempotencia), esperado-mas-monitorado (ex.:
 * CannotAcquireLockException do expurgo de particao, que hoje chega ao cliente como 409
 * tambem -- ver apps/contratocommand/CLAUDE.md, "Codigos de erro") e colapso real (o resto).
 *
 * <p>Os dois primeiros buckets sao tratados como sucesso do ponto de vista do Gatling (o
 * cenario aceita 201 OU 409 como resultado esperado de uma criacao concorrente), para que a
 * taxa de "KO" reportada pelo Gatling corresponda a colapso real, nao a regra de negocio do
 * proprio sistema sob concorrencia. As contagens de cada bucket sao mantidas aqui para o
 * relatorio final (tasks.md 7.4/8.2), porque o Gatling nao discrimina isso nativamente.
 */
public final class ErroClassificador {

    private static final AtomicLong ESPERADO_POR_DESIGN = new AtomicLong(0);
    private static final AtomicLong ESPERADO_MAS_MONITORADO = new AtomicLong(0);
    private static final AtomicLong COLAPSO_REAL = new AtomicLong(0);
    private static final AtomicLong SUCESSO = new AtomicLong(0);

    private ErroClassificador() {
    }

    /**
     * Classifica um status HTTP observado num request de criacao de autorizacao.
     * 201: sucesso. 409: esperado-por-design (idempotencia) OU esperado-mas-monitorado
     * (lock de particao do expurgo) -- o corpo da resposta nao distingue os dois casos hoje
     * (ambos usam LayoutErrosApiResponse), entao ambos contam no mesmo bucket "esperado" para
     * fins deste classificador; refinar exigiria inspecionar a mensagem de erro, fora de
     * escopo desta primeira versao. Qualquer outro status (5xx, timeout) e colapso real.
     */
    public static void classificarCriacao(int statusHttp) {
        if (statusHttp == 201) {
            SUCESSO.incrementAndGet();
        } else if (statusHttp == 409) {
            ESPERADO_POR_DESIGN.incrementAndGet();
        } else {
            COLAPSO_REAL.incrementAndGet();
        }
    }

    /** Classifica um status HTTP observado num request de leitura/decisao/cancelamento. */
    public static void classificarGenerico(int statusHttp) {
        if (statusHttp >= 200 && statusHttp < 300) {
            SUCESSO.incrementAndGet();
        } else if (statusHttp == 409 || statusHttp == 422) {
            // 422 cobre regra de negocio (ex.: decisao em autorizacao ja resolvida) -- tambem
            // esperado-por-design sob carga concorrente, nao colapso.
            ESPERADO_POR_DESIGN.incrementAndGet();
        } else {
            COLAPSO_REAL.incrementAndGet();
        }
    }

    public static long taxaColapsoReal() {
        long total = SUCESSO.get() + ESPERADO_POR_DESIGN.get() + ESPERADO_MAS_MONITORADO.get() + COLAPSO_REAL.get();
        return total == 0 ? 0 : (100 * COLAPSO_REAL.get()) / total;
    }

    public static String resumo() {
        return "sucesso=%d esperado-por-design=%d esperado-mas-monitorado=%d colapso-real=%d"
                .formatted(SUCESSO.get(), ESPERADO_POR_DESIGN.get(), ESPERADO_MAS_MONITORADO.get(), COLAPSO_REAL.get());
    }
}
