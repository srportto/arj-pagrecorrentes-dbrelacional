package br.com.srportto.contratocommand.application.defaultservice.contratacao.rules;

import br.com.srportto.contratocommand.application.defaultservice.contratacao.ContratacaoRule;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ValorLimiteContrato implements ContratacaoRule {

    @Override
    public boolean aceita(CriarAutorizacaoRequest request) {
        return true;
    }

    @Override
    public void validar(CriarAutorizacaoRequest request) {
        var tipoProduto = request.tipoProduto();

        switch (tipoProduto) {
            case "PIX_AUTO" -> {
                if (request.valor().compareTo(new BigDecimal("1000000")) > 0) {
                    throw new BusinessException("Valor contratacao invalido");
                }
            }
            case "DDA_AUTO" -> {
                if (request.valor().compareTo(new BigDecimal("250000")) > 0) {
                    throw new BusinessException("Valor contratacao invalido");
                }
            }
            default -> throw new BusinessException(String.format("Nao ha configuracao de valor limite para o produto %s",tipoProduto));
        }
    }
}
