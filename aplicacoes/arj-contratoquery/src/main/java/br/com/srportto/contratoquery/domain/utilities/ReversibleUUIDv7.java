package br.com.srportto.contratoquery.domain.utilities;

import java.security.SecureRandom;
import java.util.UUID;

public class ReversibleUUIDv7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static UUID generate(int identifier) {
        if (identifier < 0 || identifier > 9999) {
            throw new IllegalArgumentException("O identificador deve ter até 4 posições (0 a 9999).");
        }

        long timestamp = System.currentTimeMillis();
        long randA = RANDOM.nextInt(4096);
        long highBits = (timestamp << 16) | (7L << 12) | randA;

        long variant = 2L << 62;
        long randB = RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL;
        randB &= 0xFFFFFFFFFFFF0000L;
        long lowBits = variant | randB | (identifier & 0xFFFFL);

        return new UUID(highBits, lowBits);
    }

    public static int extract(UUID uuid) {
        if (uuid.version() != 7) {
            throw new IllegalArgumentException("O UUID fornecido não é da versão 7.");
        }
        long lowBits = uuid.getLeastSignificantBits();
        return (int) (lowBits & 0xFFFFL);
    }
}
