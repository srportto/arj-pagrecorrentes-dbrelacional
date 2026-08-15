package br.com.srportto.contratocommand.application.cancelamento.rules;

import br.com.srportto.contratocommand.application.cancelamento.CancelamentoContext;
import br.com.srportto.contratocommand.application.cancelamento.CancelamentoRule;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;


@Component
@Order(5) // Roda antes de TransicaoStatusValida (10): produto divergente é erro mais específico que status
public class TipoProdutoCancelamento implements CancelamentoRule {

    @Override
    public boolean aceita(CancelamentoContext context) {
        return true;
    }

    @Override
    public void validar(CancelamentoContext context) {
        var produtoHeader = context.tipoProduto();
        var produtoDaAutorizacao = context.tipoProdutoAutorizacao();

        if (!produtoHeader.name().equalsIgnoreCase(produtoDaAutorizacao.name())) {
            throw new BusinessException("TipoProduto do request de cancelamento diverge do atrelado ao idAutorizacao");
        }
    }

}
