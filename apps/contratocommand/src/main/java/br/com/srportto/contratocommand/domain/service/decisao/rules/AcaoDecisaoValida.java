package br.com.srportto.contratocommand.domain.service.decisao.rules;

import br.com.srportto.contratocommand.domain.port.in.DecidirAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.service.decisao.DecisaoRule;
import br.com.srportto.contratocommand.domain.enums.AcaoDecisao;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Roda antes das demais: garante que `acao` resolve para um valor conhecido de {@link AcaoDecisao}. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AcaoDecisaoValida implements DecisaoRule {

    @Override
    public boolean aceita(DecidirAutorizacaoCommand context) {
        return true;
    }

    @Override
    public void validar(DecidirAutorizacaoCommand context) {
        AcaoDecisao.obterAcaoDecisaoEnumPorNome(context.acao());
    }

}
