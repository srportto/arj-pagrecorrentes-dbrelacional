package br.com.srportto.temporizaautorizacao.application.agendamento;

import br.com.srportto.temporizaautorizacao.application.eventos.AutorizacaoEventoPayload;
import br.com.srportto.temporizaautorizacao.shared.config.TemporizacaoProperties;
import br.com.srportto.temporizaautorizacao.shared.exceptions.AgendamentoInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.ZoneId;

/** Agenda a expiração: vencimento = data_hora_inclusao (do payload, não do instante de consumo) + prazo. */
@Service
public class AgendarExpiracaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(AgendarExpiracaoUseCase.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgendamentoRepository agendamentoRepository;
    private final TemporizacaoProperties properties;

    public AgendarExpiracaoUseCase(AgendamentoRepository agendamentoRepository, TemporizacaoProperties properties) {
        this.agendamentoRepository = agendamentoRepository;
        this.properties = properties;
    }

    public void agendar(String mensagemJson) {
        AutorizacaoEventoPayload payload = desserializar(mensagemJson);

        if (payload.idAutorizacao() == null || payload.dataHoraInclusao() == null) {
            throw new AgendamentoInvalidoException(
                    "Payload sem id_autorizacao ou data_hora_inclusao, necessários ao agendamento");
        }

        var vencimento = payload.dataHoraInclusao()
                .plus(properties.prazo())
                .atZone(ZoneId.systemDefault())
                .toInstant();

        agendamentoRepository.agendar(payload.idAutorizacao(), vencimento);

        log.info("Expiração agendada: idAutorizacao={} vencimento={}", payload.idAutorizacao(), vencimento);
    }

    private AutorizacaoEventoPayload desserializar(String mensagemJson) {
        try {
            return objectMapper.readValue(mensagemJson, AutorizacaoEventoPayload.class);
        } catch (JacksonException e) {
            throw new AgendamentoInvalidoException(
                    "Campo do payload não pôde ser convertido para o tipo esperado: campo="
                            + e.getPathReference() + " causa=" + e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            throw new AgendamentoInvalidoException(
                    "Body da mensagem não é um JSON válido para o payload de autorização ("
                            + e.getClass().getSimpleName() + ")");
        }
    }

}
