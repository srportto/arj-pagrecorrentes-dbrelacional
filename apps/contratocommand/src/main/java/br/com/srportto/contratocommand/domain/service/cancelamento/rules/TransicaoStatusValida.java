package br.com.srportto.contratocommand.domain.service.cancelamento.rules;

import br.com.srportto.contratocommand.domain.port.in.CancelarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.service.cancelamento.CancelamentoRule;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Rejeita cancelamento se o status atual não tem aresta para CANCELADA no grafo de
 * {@link StatusAutorizacao} — só ATIVA tem; CANCELADA/REJEITADA/EXPIRADA/FINALIZADA não.
 */
@Component
@Order(10) // Roda após TipoProdutoCancelamento (5): produto divergente é erro mais específico que status
public class TransicaoStatusValida implements CancelamentoRule {

    @Override
    public boolean aceita(CancelarAutorizacaoCommand context) {
        return true;
    }

    @Override
    public void validar(CancelarAutorizacaoCommand context) {
        var statusAtual = context.statusAtual();

        boolean transicaoPermitida = statusAtual.podeTransicionarPara(StatusAutorizacao.CANCELADA);

        if (!transicaoPermitida) {
            throw new BusinessException(String.format(
                    "Autorização com status atual '%s' não permite cancelamento (esperado apenas ATIVA)",
                    statusAtual));
        }
    }
}
