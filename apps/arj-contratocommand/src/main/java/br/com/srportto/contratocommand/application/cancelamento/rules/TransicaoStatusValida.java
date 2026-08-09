package br.com.srportto.contratocommand.application.cancelamento.rules;

import br.com.srportto.contratocommand.application.cancelamento.CancelamentoContext;
import br.com.srportto.contratocommand.application.cancelamento.CancelamentoRule;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Valida a transição de cancelamento contra o grafo de {@link StatusAutorizacao}, a partir do
 * status atual lido do banco. Rejeita o cancelamento se o status atual não permite a transição
 * para CANCELADA — por exemplo, cancelar uma autorização já CANCELADA, REJEITADA, EXPIRADA ou
 * FINALIZADA é um erro de negócio, não uma operação silenciosa.
 *
 * Único caminho de {@link StatusAutorizacao} com aresta para CANCELADA é ATIVA
 * (ATIVA → CANCELADA/FINALIZADA/REJEITADA) — nenhum outro status permite cancelamento.
 */
@Component
@Order(10) // Roda após TipoProdutoCancelamento (5): produto divergente é erro mais específico que status
public class TransicaoStatusValida implements CancelamentoRule {

    @Override
    public boolean aceita(CancelamentoContext context) {
        return true;
    }

    @Override
    public void validar(CancelamentoContext context) {
        var statusAtual = context.statusAtual();

        // Valida se o status atual permite transição para CANCELADA consultando o grafo
        // de estados (máquina de estados de autorização).
        boolean transicaoPermitida = statusAtual.podeTransicionarPara(StatusAutorizacao.CANCELADA);

        if (!transicaoPermitida) {
            throw new BusinessException(String.format(
                    "Autorização com status atual '%s' não permite cancelamento (esperado apenas ATIVA)",
                    statusAtual));
        }
    }
}
