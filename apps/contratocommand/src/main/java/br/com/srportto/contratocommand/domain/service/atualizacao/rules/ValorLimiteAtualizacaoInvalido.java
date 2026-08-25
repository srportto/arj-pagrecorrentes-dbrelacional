package br.com.srportto.contratocommand.domain.service.atualizacao.rules;

import br.com.srportto.contratocommand.domain.port.in.AtualizarDadosRecorrenciaCommand;
import br.com.srportto.contratocommand.domain.service.atualizacao.AtualizacaoRule;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Primeira regra de negócio de {@code valorLimite} — o campo nunca teve validação alguma até esta
 * change (ver design.md, D5). Rejeita valor menor ou igual a zero quando informado; sem teto por
 * produto, sem dado hoje para calibrá-lo.
 */
@Component
@Order(20)
public class ValorLimiteAtualizacaoInvalido implements AtualizacaoRule {

    @Override
    public boolean aceita(AtualizarDadosRecorrenciaCommand context) {
        return true;
    }

    @Override
    public void validar(AtualizarDadosRecorrenciaCommand context) {
        var valorLimite = context.valorLimite();

        if (valorLimite != null && valorLimite.signum() <= 0) {
            throw new BusinessException(
                    "O valorLimite deve ser maior que zero. Valor informado: " + valorLimite);
        }
    }

}
