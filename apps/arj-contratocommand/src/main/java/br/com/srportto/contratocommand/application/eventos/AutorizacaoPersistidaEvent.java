package br.com.srportto.contratocommand.application.eventos;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;

/**
 * Evento interno publicado apos uma persistencia confirmada de {@link Autorizacao}
 * (criacao ou cancelamento). Consumido apenas apos o commit da transacao — ver
 * {@link AutorizacaoEventoPublisher}.
 */
public record AutorizacaoPersistidaEvent(Autorizacao autorizacao, TipoEventoAutorizacao tipo) {
}
