package br.com.srportto.contratocommand.domain.service.atualizacao.rules;

import br.com.srportto.contratocommand.domain.port.in.AtualizarDadosRecorrenciaCommand;
import br.com.srportto.contratocommand.domain.service.atualizacao.AtualizacaoRule;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Restringe a atualização de dados a autorizações ATIVA. Não é uma transição do grafo de
 * {@link StatusAutorizacao} — a operação não muda status, por isso a restrição vira rule, não
 * extensão da tabela de transições (ver design.md, D2).
 */
@Component
@Order(10) // Roda após TipoProdutoAtualizacao (5): produto divergente é erro mais específico que status
public class StatusPermiteAtualizacao implements AtualizacaoRule {

    @Override
    public boolean aceita(AtualizarDadosRecorrenciaCommand context) {
        return true;
    }

    @Override
    public void validar(AtualizarDadosRecorrenciaCommand context) {
        var statusAtual = context.statusAtual();

        if (statusAtual != StatusAutorizacao.ATIVA) {
            throw new BusinessException(String.format(
                    "Autorização com status atual '%s' não permite atualização de dados (esperado ATIVA)",
                    statusAtual));
        }
    }
}
