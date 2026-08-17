package br.com.srportto.eventosconsumer.infrastructure.messaging;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import br.com.srportto.eventosconsumer.domain.model.EventoAutorizacaoConsumido;

/**
 * Único ponto de tradução do record Avro gerado para o modelo de domínio puro — espelha o
 * {@code EventoAutorizacaoAvroMapper} do {@code autorizacaostatus-producer} (mesma família de
 * evento, direção oposta: lá domínio→Avro, aqui Avro→domínio).
 */
public final class EventoAutorizacaoAvroMapper {

    private EventoAutorizacaoAvroMapper() {
    }

    public static EventoAutorizacaoConsumido paraDominio(EventoAutorizacao avro) {
        return new EventoAutorizacaoConsumido(avro.getIdAutorizacao(), avro.getStatus());
    }
}
