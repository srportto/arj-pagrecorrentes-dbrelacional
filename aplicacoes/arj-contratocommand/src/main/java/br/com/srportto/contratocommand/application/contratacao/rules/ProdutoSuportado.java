package br.com.srportto.contratocommand.application.contratacao.rules;

import br.com.srportto.contratocommand.application.contratacao.ContratacaoRule;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
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
    public boolean aceita(CriarAutorizacaoRequest request) {
        return true;
    }

    @Override
    public void validar(CriarAutorizacaoRequest request) {
        var tipoProduto = request.tipoProduto();

        boolean suportado = tipoProduto != null && Arrays.stream(TipoProduto.values())
                .anyMatch(produto -> produto.name().equalsIgnoreCase(tipoProduto));

        if (!suportado) {
            throw new BusinessException("Produto nao suportado ou invalido (tipoProduto: " + tipoProduto + ")");
        }
    }

}
