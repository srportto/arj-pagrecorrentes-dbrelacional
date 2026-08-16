package br.com.srportto.contratoquery.domain.model;

import java.util.List;

/** Resultado de uma busca paginada: conteúdo + total — sem depender de {@code Page} do Spring Data (D7). */
public record PaginaAutorizacoes(List<Autorizacao> conteudo, long totalElementos) {
}
