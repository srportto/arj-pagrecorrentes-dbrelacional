package br.com.srportto.contratocommand.domain.service.atualizacao.rules;

import br.com.srportto.contratocommand.domain.port.in.AtualizarDadosRecorrenciaCommand;
import br.com.srportto.contratocommand.domain.service.atualizacao.AtualizacaoRule;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Duplica a checagem de {@code DataFimVigenciaInvalida} (criação) para
 * {@link AtualizarDadosRecorrenciaCommand} — mesma convenção do código de duplicar por tipo de
 * comando em vez de compartilhar abstração entre {@code Rule<T>} de tipos diferentes (ver
 * design.md, D4). Só valida quando o campo vem preenchido no PATCH.
 */
@Component
@Order(15)
public class DataFimVigenciaInvalidaAtualizacao implements AtualizacaoRule {

    @Override
    public boolean aceita(AtualizarDadosRecorrenciaCommand context) {
        return true;
    }

    @Override
    public void validar(AtualizarDadosRecorrenciaCommand context) {
        var dataFimVigencia = context.dataFimVigencia();

        if (dataFimVigencia != null && dataFimVigencia.isBefore(LocalDate.now())) {
            throw new BusinessException(
                    "A data de fim de vigencia nao pode ser no passado. Data informada: " + dataFimVigencia);
        }
    }

}
