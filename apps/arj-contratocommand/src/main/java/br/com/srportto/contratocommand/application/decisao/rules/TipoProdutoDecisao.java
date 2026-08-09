package br.com.srportto.contratocommand.application.decisao.rules;

import br.com.srportto.contratocommand.application.decisao.DecisaoContext;
import br.com.srportto.contratocommand.application.decisao.DecisaoRule;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import org.springframework.stereotype.Component;

/** Espelha {@code TipoProdutoCancelamento}: o produto do header deve bater com o persistido. */
@Component
public class TipoProdutoDecisao implements DecisaoRule {

    @Override
    public boolean aceita(DecisaoContext context) {
        return true;
    }

    @Override
    public void validar(DecisaoContext context) {
        var produtoHeader = context.tipoProduto();
        var produtoDaAutorizacao = context.tipoProdutoAutorizacao();

        if (!produtoHeader.name().equalsIgnoreCase(produtoDaAutorizacao.name())) {
            throw new BusinessException("TipoProduto do request de decisao diverge do atrelado ao idAutorizacao");
        }
    }

}
