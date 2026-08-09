package br.com.srportto.temporizaautorizacao.application.expiracao;

import java.util.UUID;

/**
 * Porta de saída: aciona a rota de decisão do arj-contratocommand com {@code acao=EXPIRAR}.
 *
 * <p>O contrato é por comportamento: retornar normalmente (2xx ou 4xx, incluindo o 422 de
 * "status já não é RECEBIDA") significa trabalho concluído — o chamador deve confirmar (XACK).
 * Lançar {@link br.com.srportto.temporizaautorizacao.shared.exceptions.ExpiracaoRetryavelException}
 * (5xx, timeout, falha de conexão) significa que o trabalho NÃO foi concluído.
 */
public interface DecisaoAutorizacaoClient {

    void expirar(UUID idAutorizacao);

}
