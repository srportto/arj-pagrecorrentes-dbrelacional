package br.com.srportto.autorizacaostatusproducer.infrastructure.messaging;

import br.com.srportto.autorizacaostatusproducer.domain.model.EventoAutorizacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Testes do EventoAutorizacaoAvroMapper")
class EventoAutorizacaoAvroMapperTest {

    private final EventoAutorizacaoAvroMapper mapper = new EventoAutorizacaoAvroMapper();

    private EventoAutorizacao eventoCompleto(BigDecimal valor, BigDecimal valorLimite) {
        return new EventoAutorizacao(
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
                valorLimite,
                1,
                2,
                1,
                1,
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
                "{\"chave\":\"valor\"}");
    }

    @Test
    @DisplayName("mapeia os campos do domínio para o record Avro")
    void mapeiaCamposCorretamente() {
        EventoAutorizacao evento = eventoCompleto(new BigDecimal("150.00"), new BigDecimal("100.00"));

        var avro = mapper.paraAvro(evento);

        assertEquals(evento.idAutorizacao(), avro.getIdAutorizacao());
        assertEquals(evento.idParticaoConta(), avro.getIdParticaoConta());
        assertEquals(evento.dataFimVigencia(), avro.getDataFimVigencia());
        assertEquals(evento.tipoProduto(), avro.getTipoProduto());
        assertEquals(evento.status(), avro.getStatus());
        assertEquals(evento.codigoCanalContratacao(), avro.getCodigoCanalContratacao());
        assertEquals(evento.dataHoraUltimaAtlz(), avro.getDataHoraUltimaAtlz());
        assertEquals(evento.metadados(), avro.getMetadados());
    }

    @Test
    @DisplayName("aplica setScale(2) no valor e no valor_limite, mesmo com scale divergente")
    void aplicaScaleNosDecimais() {
        EventoAutorizacao evento = eventoCompleto(new BigDecimal("150.5"), new BigDecimal("100"));

        var avro = mapper.paraAvro(evento);

        assertEquals(new BigDecimal("150.50"), avro.getValor());
        assertEquals(new BigDecimal("100.00"), avro.getValorLimite());
    }

    @Test
    @DisplayName("decimais nulos permanecem nulos após o mapeamento")
    void decimaisNulosPermanecemNulos() {
        EventoAutorizacao evento = eventoCompleto(null, null);

        var avro = mapper.paraAvro(evento);

        assertNull(avro.getValor());
        assertNull(avro.getValorLimite());
    }

}
