package br.com.srportto.eventosconsumer.domain.port.in;

import br.com.srportto.eventosconsumer.domain.model.EventoAutorizacaoConsumido;

/** Porta de entrada: processa (loga) o evento de autorização consumido do tópico Kafka. */
public interface ProcessarEventoAutorizacaoUseCase {

    void processar(EventoAutorizacaoConsumido evento);

}
