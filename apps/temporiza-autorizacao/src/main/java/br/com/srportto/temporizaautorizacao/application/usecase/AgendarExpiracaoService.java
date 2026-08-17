package br.com.srportto.temporizaautorizacao.application.usecase;

import br.com.srportto.temporizaautorizacao.domain.exception.AgendamentoInvalidoException;
import br.com.srportto.temporizaautorizacao.domain.model.CalculadoraVencimento;
import br.com.srportto.temporizaautorizacao.domain.port.in.AgendarExpiracaoUseCase;
import br.com.srportto.temporizaautorizacao.domain.port.out.AgendamentoRepository;
import br.com.srportto.temporizaautorizacao.infrastructure.config.TemporizacaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/** Agenda a expiração: vencimento = data_hora_inclusao (do payload, não do instante de consumo) + prazo. */
@Service
public class AgendarExpiracaoService implements AgendarExpiracaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(AgendarExpiracaoService.class);

    private final AgendamentoRepository agendamentoRepository;
    private final TemporizacaoProperties properties;

    public AgendarExpiracaoService(AgendamentoRepository agendamentoRepository, TemporizacaoProperties properties) {
        this.agendamentoRepository = agendamentoRepository;
        this.properties = properties;
    }

    @Override
    public void agendar(UUID idAutorizacao, LocalDateTime dataHoraInclusao) {
        if (idAutorizacao == null || dataHoraInclusao == null) {
            throw new AgendamentoInvalidoException(
                    "Payload sem id_autorizacao ou data_hora_inclusao, necessários ao agendamento");
        }

        var vencimento = CalculadoraVencimento.calcular(dataHoraInclusao, properties.prazo());

        agendamentoRepository.agendar(idAutorizacao, vencimento);

        log.info("Expiração agendada: idAutorizacao={} vencimento={}", idAutorizacao, vencimento);
    }

}
