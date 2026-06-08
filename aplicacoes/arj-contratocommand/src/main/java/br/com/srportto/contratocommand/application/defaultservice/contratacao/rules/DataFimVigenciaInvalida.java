package br.com.srportto.contratocommand.application.defaultservice.contratacao.rules;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

import br.com.srportto.contratocommand.application.defaultservice.contratacao.ContratacaoRule;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;

@Component
public class DataFimVigenciaInvalida implements ContratacaoRule {

    @Override
    public boolean aceita(CriarAutorizacaoRequest request) {
        return true;
    }

    @Override
    public void validar(CriarAutorizacaoRequest request) {
        var dataFimVigencia = request.dataFimVigencia();

        if (dataFimVigencia != null && dataFimVigencia.isBefore(LocalDate.now())) {
            throw new BusinessException(
                    "A data de fim de vigencia nao pode ser no passado. Data informada: " + dataFimVigencia);
        }
    }

}
