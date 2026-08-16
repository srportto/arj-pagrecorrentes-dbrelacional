package br.com.srportto.autorizacaostatusproducer.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@DisplayName("Testes do IdempotenciaKeyGenerator")
class IdempotenciaKeyGeneratorTest {

    private final IdempotenciaKeyGenerator generator = new IdempotenciaKeyGenerator();

    @Test
    @DisplayName("a mesma transição de estado gera sempre a mesma key")
    void mesmaTransicaoGeraMesmaKey() {
        UUID id = UUID.randomUUID();
        LocalDateTime dataHora = LocalDateTime.of(2026, 7, 26, 10, 30, 0);

        String key1 = generator.gerar(id, dataHora);
        String key2 = generator.gerar(id, dataHora);

        assertEquals(key1, key2);
    }

    @Test
    @DisplayName("transições distintas (mesma autorização, data/hora diferente) geram keys distintas")
    void transicoesDistintasGeramKeysDistintas() {
        UUID id = UUID.randomUUID();

        String keyCriacao = generator.gerar(id, LocalDateTime.of(2026, 7, 26, 10, 0, 0));
        String keyCancelamento = generator.gerar(id, LocalDateTime.of(2026, 7, 26, 11, 0, 0));

        assertNotEquals(keyCriacao, keyCancelamento);
    }

    @Test
    @DisplayName("autorizações diferentes geram keys distintas mesmo com a mesma data/hora")
    void autorizacoesDiferentesGeramKeysDistintas() {
        LocalDateTime dataHora = LocalDateTime.of(2026, 7, 26, 10, 0, 0);

        String key1 = generator.gerar(UUID.randomUUID(), dataHora);
        String key2 = generator.gerar(UUID.randomUUID(), dataHora);

        assertNotEquals(key1, key2);
    }

    @Test
    @DisplayName("a key é um hash SHA-256 em hexadecimal (64 caracteres)")
    void keyEhHashSha256Hexadecimal() {
        String key = generator.gerar(UUID.randomUUID(), LocalDateTime.now());

        assertEquals(64, key.length());
        assertEquals(key.toLowerCase(), key);
    }

}
