package br.com.srportto.contratocommand.domain.model;

import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Testes de AutorizacaoId")
class AutorizacaoIdTest {

    @Test
    @DisplayName("UUID válido constrói o value object com o mesmo valor")
    void uuidValidoConstroi() {
        UUID uuid = UUID.randomUUID();

        AutorizacaoId id = AutorizacaoId.de(uuid.toString());

        assertEquals(uuid, id.valor());
    }

    @Test
    @DisplayName("string malformada lança BusinessException")
    void stringMalformadaLancaBusinessException() {
        assertThrows(BusinessException.class, () -> AutorizacaoId.de("nao-e-uuid"));
    }

    @Test
    @DisplayName("string vazia lança BusinessException")
    void stringVaziaLancaBusinessException() {
        assertThrows(BusinessException.class, () -> AutorizacaoId.de(""));
    }

    @Test
    @DisplayName("null lança BusinessException")
    void nuloLancaBusinessException() {
        assertThrows(BusinessException.class, () -> AutorizacaoId.de(null));
    }

    @Test
    @DisplayName("construtor compacto rejeita UUID nulo")
    void construtorCompactoRejeitaUuidNulo() {
        assertThrows(BusinessException.class, () -> new AutorizacaoId(null));
    }
}
