package br.com.srportto.contratocommand.domain.service.contratacao.rules;

import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.service.contratacao.ContratacaoRule;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.springframework.stereotype.Component;

/** Limite de valor por produto vem de {@code TipoProduto.getValorLimiteContratacao()} — produto novo sem limite configurado não compila. */
@Component
public class ValorLimiteContrato implements ContratacaoRule {

    @Override
    public boolean aceita(CriarAutorizacaoCommand contexto) {
        return true;
    }

    @Override
    public void validar(CriarAutorizacaoCommand contexto) {
        var limite = contexto.tipoProduto().getValorLimiteContratacao();

        if (contexto.valor().signum() <= 0) {
            throw new BusinessException("Valor de contratacao deve ser maior que zero");
        }

        if (contexto.valor().compareTo(limite) > 0) {
            throw new BusinessException("Valor contratacao invalido");
        }
    }
}
