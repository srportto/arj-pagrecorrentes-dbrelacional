package br.com.srportto.contratocommand.infrastructure.persistence;

import java.util.UUID;

public class IdContaUUIDPartitionDistributor {

    /** Quantidade de partições quentes (faixa 0..888). */
    private static final int QUANTIDADE_PARTICOES_QUENTES = 889;

    public static int getPartitionFast(UUID uuid) {
        int hash = uuid.hashCode();
        // Math.floorMod (não Math.abs % n): Math.abs(Integer.MIN_VALUE) continua negativo.
        return Math.floorMod(hash, QUANTIDADE_PARTICOES_QUENTES);
    }

}
