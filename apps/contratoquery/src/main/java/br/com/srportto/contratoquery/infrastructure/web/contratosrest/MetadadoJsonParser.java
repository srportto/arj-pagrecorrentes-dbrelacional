package br.com.srportto.contratoquery.infrastructure.web.contratosrest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Único ponto de parse do {@code metadados} (jsonb cru, como {@code String}) para os DTOs de
 * resposta — antes cada DTO tinha seu próprio {@code ObjectMapper} e engolia a falha em silêncio.
 */
final class MetadadoJsonParser {

    private static final Logger log = LoggerFactory.getLogger(MetadadoJsonParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MetadadoJsonParser() {
    }

    static JsonNode parse(String metadados) {
        if (metadados == null || metadados.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(metadados);
        } catch (Exception e) {
            log.warn("Falha ao desserializar metadados (campo removido da resposta): {}", e.getMessage());
            return null;
        }
    }
}
