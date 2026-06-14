package br.com.srportto.contratocommand.application.defaultservice.contratacao.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes da regra MetadadoRule")
class MetadadoRuleTest {

    private final MetadadoRule regra = new MetadadoRule();
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String raw) {
        return mapper.readTree(raw);
    }

    @Test
    @DisplayName("metadado nulo é aceito")
    void metadadoNulo() {
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarRequest(
                "PIX_AUTO", new BigDecimal("10"), LocalDate.now().plusDays(1), null)));
    }

    @Test
    @DisplayName("metadado com nome/apelido dentro do limite é aceito")
    void metadadoValido() {
        JsonNode meta = json("{\"nomePessoaRecebedora\":\"Joao\",\"apelidoPessoaRecebedora\":\"Jo\"}");
        assertDoesNotThrow(() -> regra.validar(TestFixtures.criarRequest(
                "PIX_AUTO", new BigDecimal("10"), LocalDate.now().plusDays(1), meta)));
    }

    @Test
    @DisplayName("nomePessoaRecebedora acima de 255 caracteres lança BusinessException")
    void nomeMuitoLongo() {
        JsonNode meta = json("{\"nomePessoaRecebedora\":\"" + "a".repeat(256) + "\"}");
        assertThrows(BusinessException.class, () -> regra.validar(TestFixtures.criarRequest(
                "PIX_AUTO", new BigDecimal("10"), LocalDate.now().plusDays(1), meta)));
    }

    @Test
    @DisplayName("apelidoPessoaRecebedora acima de 255 caracteres lança BusinessException")
    void apelidoMuitoLongo() {
        JsonNode meta = json("{\"apelidoPessoaRecebedora\":\"" + "b".repeat(256) + "\"}");
        assertThrows(BusinessException.class, () -> regra.validar(TestFixtures.criarRequest(
                "PIX_AUTO", new BigDecimal("10"), LocalDate.now().plusDays(1), meta)));
    }
}
