package br.com.srportto.autorizacaostatusproducer.application.eventos;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Testes do EventoAutorizacaoConverter")
class EventoAutorizacaoConverterTest {

    private final EventoAutorizacaoConverter converter = new EventoAutorizacaoConverter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AutorizacaoEventoPayload payloadCompleto(BigDecimal valor) {
        JsonNode metadados = objectMapper.readTree("{\"chave\":\"valor\"}");
        return new AutorizacaoEventoPayload(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                950,
                LocalDate.of(2027, 1, 1),
                1L,
                4,
                "motivo",
                LocalDate.of(2026, 1, 1),
                LocalDateTime.of(2026, 7, 26, 10, 0, 0),
                LocalDateTime.of(2026, 7, 26, 10, 0, 0),
                valor,
                "empresa",
                new BigDecimal("100.00"),
                (short) 1,
                (short) 2,
                (short) 1,
                (short) 1,
                "canal",
                "descricao",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                metadados);
    }

    @Test
    @DisplayName("mapeia os campos do payload para o record Avro")
    void mapeiaCamposCorretamente() {
        AutorizacaoEventoPayload payload = payloadCompleto(new BigDecimal("150.00"));

        EventoAutorizacao evento = converter.converter(payload);

        assertEquals(payload.idAutorizacao(), evento.getIdAutorizacao());
        assertEquals(payload.idParticaoConta(), evento.getIdParticaoConta());
        assertEquals(payload.dataFimVigencia(), evento.getDataFimVigencia());
        assertEquals(payload.tipoProduto(), evento.getTipoProduto());
        assertEquals(payload.status(), evento.getStatus());
        assertEquals(payload.codigoCanalContratacao(), evento.getCodigoCanalContratacao());
        assertEquals(payload.dataHoraUltimaAtualizacao(), evento.getDataHoraUltimaAtlz());
        assertEquals("{\"chave\":\"valor\"}", evento.getMetadados());
    }

    @Test
    @DisplayName("aplica setScale(2) no valor e no valor_limite, mesmo com scale divergente")
    void aplicaScaleNosDecimais() {
        AutorizacaoEventoPayload payload = payloadCompleto(new BigDecimal("150.5"));

        EventoAutorizacao evento = converter.converter(payload);

        assertEquals(new BigDecimal("150.50"), evento.getValor());
        assertEquals(new BigDecimal("100.00"), evento.getValorLimite());
    }

    @Test
    @DisplayName("campos nulos do payload permanecem nulos no evento Avro")
    void camposNulosPermanecemNulos() {
        AutorizacaoEventoPayload payload = payloadCompleto(null);

        EventoAutorizacao evento = converter.converter(payload);

        assertNull(evento.getValor());
        assertNull(evento.getCodigoCanalCancelamento());
        assertNull(evento.getDataHoraCancelamento());
        assertNull(evento.getMotivoCancelamento());
    }

}
