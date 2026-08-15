package br.com.srportto.contratocommand.domain.event;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;

/** Evento interno publicado após persistência confirmada; consumido só após o commit — ver {@link AutorizacaoEventoPublisher}. */
public record AutorizacaoPersistidaEvent(Autorizacao autorizacao) {
}
