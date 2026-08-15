package br.com.srportto.temporizaautorizacao.application.expiracao;

import java.util.UUID;

/**
 * Porta de saída: aciona a decisão do contratocommand com {@code acao=EXPIRAR}. Contrato por
 * comportamento: retorno normal (2xx/4xx) = concluído, confirmar (XACK); lançar
 * {@link br.com.srportto.temporizaautorizacao.shared.exceptions.ExpiracaoRetryavelException} = não concluído.
 */
public interface DecisaoAutorizacaoClient {

    void expirar(UUID idAutorizacao);

}
