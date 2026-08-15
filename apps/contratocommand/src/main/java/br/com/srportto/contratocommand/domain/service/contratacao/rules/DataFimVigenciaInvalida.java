package br.com.srportto.contratocommand.domain.service.contratacao.rules;

import br.com.srportto.contratocommand.application.contratacao.ContratacaoContext;
import br.com.srportto.contratocommand.domain.service.contratacao.ContratacaoRule;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DataFimVigenciaInvalida implements ContratacaoRule {

    @Override
    public boolean aceita(ContratacaoContext contexto) {
        return true;
    }

    @Override
    public void validar(ContratacaoContext contexto) {
        var dataFimVigencia = contexto.dados().dataFimVigencia();

        if (dataFimVigencia != null && dataFimVigencia.isBefore(LocalDate.now())) {
            throw new BusinessException(
                    "A data de fim de vigencia nao pode ser no passado. Data informada: " + dataFimVigencia);
        }
    }

}
