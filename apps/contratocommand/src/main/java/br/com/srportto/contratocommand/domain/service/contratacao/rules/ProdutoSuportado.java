package br.com.srportto.contratocommand.domain.service.contratacao.rules;

import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.service.contratacao.ContratacaoRule;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Rejeita produto não habilitado para contratação antes das demais rules de contratação, que
 * assumem um {@link TipoProduto} habilitado. A resolução de string desconhecida para enum já
 * acontece no controller ({@link TipoProduto#obterTipoProdutoEnumPorNome}) — esta rule cobre o
 * caso de um produto que existe no enum mas não está habilitado para contratar.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProdutoSuportado implements ContratacaoRule {

    @Override
    public boolean aceita(CriarAutorizacaoCommand contexto) {
        return true;
    }

    @Override
    public void validar(CriarAutorizacaoCommand contexto) {
        var tipoProduto = contexto.tipoProduto();

        if (tipoProduto == null || !tipoProduto.habilitadoParaContratar()) {
            throw new BusinessException("Produto nao suportado ou invalido (tipoProduto: " + tipoProduto + ")");
        }
    }

}
