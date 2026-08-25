package br.com.srportto.contratocommand.domain.service.atualizacao;

import br.com.srportto.contratocommand.domain.port.in.AtualizarDadosRecorrenciaCommand;
import br.com.srportto.contratocommand.domain.service.Validator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class AtualizacaoValidator implements Validator<AtualizacaoRule, AtualizarDadosRecorrenciaCommand> {

    private final List<AtualizacaoRule> atualizacaoRules;

    @Override
    public String getLogCode() {
        return "AtualizacaoValidator: Validando regras de negocio para atualizacao de dados da recorrencia";
    }

    public List<AtualizacaoRule> getRules() {
        return atualizacaoRules;
    }
}
