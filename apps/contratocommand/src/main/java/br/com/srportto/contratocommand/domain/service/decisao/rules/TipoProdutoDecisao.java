package br.com.srportto.contratocommand.domain.service.decisao.rules;

import br.com.srportto.contratocommand.domain.port.in.DecidirAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.service.decisao.DecisaoRule;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.springframework.stereotype.Component;

/** Espelha {@code TipoProdutoCancelamento}: o produto do header deve bater com o persistido. */
@Component
public class TipoProdutoDecisao implements DecisaoRule {

    @Override
    public boolean aceita(DecidirAutorizacaoCommand context) {
        return true;
    }

    @Override
    public void validar(DecidirAutorizacaoCommand context) {
        var produtoHeader = context.tipoProduto();
        var produtoDaAutorizacao = context.tipoProdutoAutorizacao();

        if (!produtoHeader.name().equalsIgnoreCase(produtoDaAutorizacao.name())) {
            throw new BusinessException("TipoProduto do request de decisao diverge do atrelado ao idAutorizacao");
        }
    }

}
