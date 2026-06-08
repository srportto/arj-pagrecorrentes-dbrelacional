package br.com.srportto.contratocommand.application.defaultservice.cancelamento.rules;

import br.com.srportto.contratocommand.application.defaultservice.cancelamento.CancelamentoRule;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import org.springframework.stereotype.Component;


@Component
public class TipoProdutoCancelamento implements CancelamentoRule {

    @Override
    public boolean aceita(CancelarAutorizacaoRequestDto request) {
        return true;
    }

    @Override
    public void validar(CancelarAutorizacaoRequestDto request) {
        var produtoHeaderRequestCancelamento = request.getProdutoHeaderRequest();
        var produtoDaAutorizacao = request.getTipoProdutoDoIdAutorizacao();

        if (!produtoHeaderRequestCancelamento.name().equalsIgnoreCase(produtoDaAutorizacao.name())) {
            throw new BusinessException("TipoProduto do request de cancelamento diverge do atrelado ao idAutorizacao");
        }
    }

}
