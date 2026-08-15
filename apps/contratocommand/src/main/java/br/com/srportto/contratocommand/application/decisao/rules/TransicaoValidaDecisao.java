package br.com.srportto.contratocommand.application.decisao.rules;

import br.com.srportto.contratocommand.application.decisao.DecisaoContext;
import br.com.srportto.contratocommand.application.decisao.DecisaoRule;
import br.com.srportto.contratocommand.domain.enums.AcaoDecisao;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * Valida a transição pedida contra o grafo de {@link StatusAutorizacao}. Torna a rota segura
 * para chamada repetida at-least-once (ex.: o temporizador de expiração): transição já
 * indisponível vira BusinessException (422), não é ignorada silenciosamente.
 */
@Component
public class TransicaoValidaDecisao implements DecisaoRule {

    @Override
    public boolean aceita(DecisaoContext context) {
        return true;
    }

    @Override
    public void validar(DecisaoContext context) {
        var acao = AcaoDecisao.obterAcaoDecisaoEnumPorNome(context.dados().acao());
        var statusAtual = context.statusAtual();

        // Exige RECEBIDA explicitamente: o grafo também permite ATIVA -> REJEITADA (outro fluxo),
        // o que deixaria expiração tardia "rejeitar" autorização já aprovada.
        if (statusAtual != StatusAutorizacao.RECEBIDA) {
            throw new BusinessException(String.format(
                    "Autorização com status atual '%s' não permite a ação %s (esperado RECEBIDA)",
                    statusAtual, acao));
        }

        boolean transicaoPermitida = switch (acao) {
            case APROVAR -> statusAtual.podeTransicionarPara(StatusAutorizacao.EM_PROCESSO_ATIVACAO)
                    && StatusAutorizacao.EM_PROCESSO_ATIVACAO.podeTransicionarPara(StatusAutorizacao.ATIVA);
            case REJEITAR, EXPIRAR -> statusAtual.podeTransicionarPara(StatusAutorizacao.REJEITADA);
        };

        if (!transicaoPermitida) {
            throw new BusinessException(String.format(
                    "Autorização com status atual '%s' não permite a ação %s", statusAtual, acao));
        }
    }

}
