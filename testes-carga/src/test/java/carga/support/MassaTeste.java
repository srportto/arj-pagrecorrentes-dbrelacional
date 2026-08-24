package carga.support;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Convencao de identificacao de massa de teste (change testes-de-carga-tps, design.md D6):
 * todo {@code idAutorizacaoEmpresa} criado por um cenario de carga usa o prefixo
 * {@code LOADTEST-}, para localizacao e limpeza posterior independente de o cenario ter
 * terminado por conclusao normal, abort automatico ou falha (ver scripts/limpar-massa-teste.sql).
 */
public final class MassaTeste {

    public static final String PREFIXO = "LOADTEST-";

    private static final AtomicLong SEQUENCIA = new AtomicLong(0);
    private static final long EXECUCAO_TIMESTAMP = System.currentTimeMillis();

    private MassaTeste() {
    }

    /** Formato: {@code LOADTEST-{timestamp da execucao}-{sequencial}}. */
    public static String novoIdAutorizacaoEmpresa() {
        return PREFIXO + EXECUCAO_TIMESTAMP + "-" + SEQUENCIA.incrementAndGet();
    }
}
