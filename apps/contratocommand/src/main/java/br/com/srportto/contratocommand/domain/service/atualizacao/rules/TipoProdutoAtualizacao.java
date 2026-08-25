package br.com.srportto.contratocommand.domain.service.atualizacao.rules;

import br.com.srportto.contratocommand.domain.port.in.AtualizarDadosRecorrenciaCommand;
import br.com.srportto.contratocommand.domain.service.atualizacao.AtualizacaoRule;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Espelha {@code TipoProdutoCancelamento}/{@code TipoProdutoDecisao}: produto do header deve bater com o persistido. */
@Component
@Order(5) // Roda antes das demais: produto divergente é erro mais específico que status/dados
public class TipoProdutoAtualizacao implements AtualizacaoRule {

    @Override
    public boolean aceita(AtualizarDadosRecorrenciaCommand context) {
        return true;
    }

    @Override
    public void validar(AtualizarDadosRecorrenciaCommand context) {
        var produtoHeader = context.tipoProduto();
        var produtoDaAutorizacao = context.tipoProdutoAutorizacao();

        if (!produtoHeader.name().equalsIgnoreCase(produtoDaAutorizacao.name())) {
            throw new BusinessException("TipoProduto do request de atualizacao diverge do atrelado ao idAutorizacao");
        }
    }

}
