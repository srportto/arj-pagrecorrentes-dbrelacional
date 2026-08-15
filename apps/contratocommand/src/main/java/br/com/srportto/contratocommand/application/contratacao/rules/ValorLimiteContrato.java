package br.com.srportto.contratocommand.application.contratacao.rules;

import br.com.srportto.contratocommand.application.contratacao.ContratacaoContext;
import br.com.srportto.contratocommand.application.contratacao.ContratacaoRule;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ValorLimiteContrato implements ContratacaoRule {

    @Override
    public boolean aceita(ContratacaoContext contexto) {
        return true;
    }

    @Override
    public void validar(ContratacaoContext contexto) {
        var dados = contexto.dados();
        var tipoProduto = dados.tipoProduto();

        switch (tipoProduto) {
            case "PIX_AUTO" -> {
                if (dados.valor().compareTo(new BigDecimal("1000000")) > 0) {
                    throw new BusinessException("Valor contratacao invalido");
                }
            }
            case "DDA_AUTO" -> {
                if (dados.valor().compareTo(new BigDecimal("250000")) > 0) {
                    throw new BusinessException("Valor contratacao invalido");
                }
            }
            default -> throw new BusinessException(String.format("Nao ha configuracao de valor limite para o produto %s",tipoProduto));
        }
    }
}
