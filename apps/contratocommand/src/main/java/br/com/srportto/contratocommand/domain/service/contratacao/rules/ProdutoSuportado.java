package br.com.srportto.contratocommand.domain.service.contratacao.rules;

import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.service.contratacao.ContratacaoRule;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Substitui a antiga seleção por strategy: rejeita produto desconhecido antes das demais
 * rules de contratação, que assumem um {@link TipoProduto} válido.
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

        boolean suportado = tipoProduto != null && Arrays.stream(TipoProduto.values())
                .filter(produto -> produto.name().equalsIgnoreCase(tipoProduto))
                .anyMatch(produto -> produto.habilitadoParaContratar());

        if (!suportado) {
            throw new BusinessException("Produto nao suportado ou invalido (tipoProduto: " + tipoProduto + ")");
        }
    }

}
