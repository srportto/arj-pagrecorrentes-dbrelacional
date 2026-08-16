package br.com.srportto.eventosconsumer.domain.port.in;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;

/** Porta de entrada: processa (loga) o evento de autorização consumido do tópico Kafka. */
public interface ProcessarEventoAutorizacaoUseCase {

    void processar(EventoAutorizacao evento);

}
