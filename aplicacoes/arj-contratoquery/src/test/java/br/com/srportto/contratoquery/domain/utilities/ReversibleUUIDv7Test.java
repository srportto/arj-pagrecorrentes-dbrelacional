package br.com.srportto.contratoquery.domain.utilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do ReversibleUUIDv7")
class ReversibleUUIDv7Test {

    @Test
    @DisplayName("generate produz UUID versão 7 e extract recupera o identificador")
    void geraEExtrai() {
        for (int id : new int[]{0, 1, 500, 888, 9999}) {
            UUID uuid = ReversibleUUIDv7.generate(id);
            assertEquals(7, uuid.version());
            assertEquals(id, ReversibleUUIDv7.extract(uuid));
        }
    }

    @Test
    @DisplayName("generate rejeita identificador fora da faixa 0..9999")
    void rejeitaIdentificadorInvalido() {
        assertThrows(IllegalArgumentException.class, () -> ReversibleUUIDv7.generate(-1));
        assertThrows(IllegalArgumentException.class, () -> ReversibleUUIDv7.generate(10000));
    }

    @Test
    @DisplayName("extract rejeita UUID que não é versão 7")
    void extractRejeitaNaoV7() {
        UUID uuidV4 = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> ReversibleUUIDv7.extract(uuidV4));
    }
}
