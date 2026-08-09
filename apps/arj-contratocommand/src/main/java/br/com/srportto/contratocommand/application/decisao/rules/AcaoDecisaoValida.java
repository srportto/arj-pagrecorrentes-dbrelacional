package br.com.srportto.contratocommand.application.decisao.rules;

import br.com.srportto.contratocommand.application.decisao.DecisaoContext;
import br.com.srportto.contratocommand.application.decisao.DecisaoRule;
import br.com.srportto.contratocommand.domain.enums.AcaoDecisao;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Roda antes das demais: garante que `acao` resolve para um valor conhecido de {@link AcaoDecisao}. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AcaoDecisaoValida implements DecisaoRule {

    @Override
    public boolean aceita(DecisaoContext context) {
        return true;
    }

    @Override
    public void validar(DecisaoContext context) {
        AcaoDecisao.obterAcaoDecisaoEnumPorNome(context.dados().acao());
    }

}
