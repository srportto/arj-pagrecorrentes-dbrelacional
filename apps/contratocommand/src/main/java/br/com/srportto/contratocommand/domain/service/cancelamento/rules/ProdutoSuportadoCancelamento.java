package br.com.srportto.contratocommand.domain.service.cancelamento.rules;

import br.com.srportto.contratocommand.application.cancelamento.CancelamentoContext;
import br.com.srportto.contratocommand.domain.service.cancelamento.CancelamentoRule;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Espelha a {@code ProdutoSuportado} da contratação: rejeita produto não habilitado para
 * cancelar antes das demais rules de cancelamento, que assumem um produto habilitado.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProdutoSuportadoCancelamento implements CancelamentoRule {

    @Override
    public boolean aceita(CancelamentoContext context) {
        return true;
    }

    @Override
    public void validar(CancelamentoContext context) {
        var tipoProduto = context.tipoProduto();

        if (!tipoProduto.habilitadoParaCancelar()) {
            throw new BusinessException("Produto nao habilitado para cancelamento (tipoProduto: " + tipoProduto + ")");
        }
    }

}
