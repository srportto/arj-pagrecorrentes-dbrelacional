package br.com.srportto.contratocommand.application.cancelamento.rules;

import br.com.srportto.contratocommand.application.cancelamento.CancelamentoContext;
import br.com.srportto.contratocommand.application.cancelamento.CancelamentoRule;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import org.springframework.stereotype.Component;


@Component
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
