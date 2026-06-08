package br.com.srportto.contratocommand.domain.utilities;

import java.security.SecureRandom;
import java.util.UUID;

public class ReversibleUUIDv7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Gera um UUIDv7 contendo um inteiro de 4 dígitos embutido em seus últimos 16 bits.
     *
     * @param identifier Inteiro de 0 a 9999.
     * @return UUID versão 7
     */
    public static UUID generate(int identifier) {
        if (identifier < 0 || identifier > 9999) {
            throw new IllegalArgumentException("O identificador deve ter até 4 posições (0 a 9999).");
        }

        // 1. Pega o timestamp atual (48 bits suportam milissegundos até o ano 10889)
        long timestamp = System.currentTimeMillis();

        // 2. Constrói os 64 bits mais significativos (High Bits)
        // 48 bits de timestamp | 4 bits de versão (7) | 12 bits aleatórios
        long randA = RANDOM.nextInt(4096); // 12 bits aleatórios (0 a 4095)
        long highBits = (timestamp << 16) | (7L << 12) | randA;

        // 3. Constrói os 64 bits menos significativos (Low Bits)
        // 2 bits de variante (10) | 46 bits aleatórios | 16 bits para o nosso identificador
        
        // Define a variante como 2 (0x8000000000000000L)
        long variant = 2L << 62; 
        
        // Gera 62 bits aleatórios
        long randB = RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL; 
        
        // Zera os últimos 16 bits do randB para dar espaço ao nosso identificador
        randB &= 0xFFFFFFFFFFFF0000L; 
        
        // Insere o identificador nos últimos 16 bits
        long lowBits = variant | randB | (identifier & 0xFFFFL);

        // 4. Retorna o objeto UUID nativo do Java
        return new UUID(highBits, lowBits);
    }

    /**
     * Extrai o inteiro de 4 dígitos que foi embutido em um UUIDv7.
     *
     * @param uuid O UUID gerado pela função generate()
     * @return O inteiro original
     */
    public static int extract(UUID uuid) {
        if (uuid.version() != 7) {
            throw new IllegalArgumentException("O UUID fornecido não é da versão 7.");
        }

        // Pega os 64 bits menos significativos e extrai apenas os últimos 16 bits (0xFFFF)
        long lowBits = uuid.getLeastSignificantBits();
        return (int) (lowBits & 0xFFFFL);
    }
}