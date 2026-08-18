package br.com.srportto.contratocommand.domain.event;

import br.com.srportto.contratocommand.domain.model.Autorizacao;

/** Evento interno publicado após persistência confirmada; consumido só após o commit, por um listener de mensageria na infraestrutura. */
public record AutorizacaoPersistidaEvent(Autorizacao autorizacao) {
}
