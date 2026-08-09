package br.com.srportto.contratocommand.application.decisao.rules;

import br.com.srportto.contratocommand.application.decisao.DecisaoContext;
import br.com.srportto.contratocommand.application.decisao.DecisaoRule;
import br.com.srportto.contratocommand.domain.enums.AcaoDecisao;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import org.springframework.stereotype.Component;

/**
 * Valida a transição pedida contra o grafo de {@link StatusAutorizacao}, a partir do status atual
 * lido do banco. Torna a rota segura para chamada repetida por um chamador at-least-once (ex.: o
 * temporizador de expiração): status que já não permite a transição é BusinessException (422), não
 * silenciosamente ignorado.
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

        // Esta rota decide apenas sobre autorizações em RECEBIDA. Checar só a alcançabilidade
        // genérica via podeTransicionarPara não bastaria: o grafo também permite ATIVA -> REJEITADA
        // (para outro fluxo de negócio), o que deixaria uma expiração tardia "rejeitar" uma
        // autorização já aprovada. Por isso o status atual precisa ser RECEBIDA explicitamente,
        // e só então a transição alvo é confirmada contra o grafo (defesa em profundidade).
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
