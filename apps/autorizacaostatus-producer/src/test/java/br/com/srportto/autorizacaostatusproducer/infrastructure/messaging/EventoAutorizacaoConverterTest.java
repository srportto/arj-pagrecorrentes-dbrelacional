package br.com.srportto.autorizacaostatusproducer.infrastructure.messaging;

import br.com.srportto.autorizacaostatusproducer.domain.model.EventoAutorizacao;
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
    @DisplayName("mapeia os campos do payload para o modelo de domínio")
    void mapeiaCamposCorretamente() {
        AutorizacaoEventoPayload payload = payloadCompleto(new BigDecimal("150.00"));

        EventoAutorizacao evento = converter.converter(payload);

        assertEquals(payload.idAutorizacao(), evento.idAutorizacao());
        assertEquals(payload.idParticaoConta(), evento.idParticaoConta());
        assertEquals(payload.dataFimVigencia(), evento.dataFimVigencia());
        assertEquals(payload.tipoProduto(), evento.tipoProduto());
        assertEquals(payload.tipoJornada(), evento.tipoJornada());
        assertEquals(payload.status(), evento.status());
        assertEquals(payload.codigoCanalContratacao(), evento.codigoCanalContratacao());
        assertEquals(payload.dataHoraUltimaAtualizacao(), evento.dataHoraUltimaAtlz());
        assertEquals("{\"chave\":\"valor\"}", evento.metadados());
    }

    @Test
    @DisplayName("não altera o scale dos decimais — isso é particularidade do Avro (ver EventoAutorizacaoAvroMapper)")
    void naoAlteraOScaleDosDecimais() {
        AutorizacaoEventoPayload payload = payloadCompleto(new BigDecimal("150.5"));

        EventoAutorizacao evento = converter.converter(payload);

        assertEquals(new BigDecimal("150.5"), evento.valor());
        assertEquals(new BigDecimal("100.00"), evento.valorLimite());
    }

    @Test
    @DisplayName("campos nulos do payload permanecem nulos no evento de domínio")
    void camposNulosPermanecemNulos() {
        AutorizacaoEventoPayload payload = payloadCompleto(null);

        EventoAutorizacao evento = converter.converter(payload);

        assertNull(evento.valor());
        assertNull(evento.codigoCanalCancelamento());
        assertNull(evento.dataHoraCancelamento());
        assertNull(evento.motivoCancelamento());
    }

}
