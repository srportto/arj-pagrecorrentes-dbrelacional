package br.com.srportto.contratoquery.domain.model;

/**
 * Modelo de dominio puro (sem frameworks) que encapsula a regra de montagem
 * da saudacao de health/disponibilidade da aplicacao.
 *
 * Record imutavel conforme convencao da arquitetura.
 */
public record SaudacaoOlamundo(String nomeAplicacao) {

	public String montarMensagem() {
		return "salve quebrada, " + nomeAplicacao + " ON!!";
	}
}
