package br.com.srportto.temporizaautorizacao.infrastructure.messaging;

import br.com.srportto.temporizaautorizacao.domain.exception.AgendamentoInvalidoException;
import br.com.srportto.temporizaautorizacao.domain.port.in.AgendarExpiracaoUseCase;
import io.awspring.cloud.sqs.annotation.SqsListener;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador de ENTRADA: consome a fila, traduz o formato de fio ({@link AutorizacaoEventoPayload})
 * em argumentos simples e delega ao use case, sem regra de negócio própria.
 * Sem {@code try/catch} de falha de negócio — classificação (ack ou retenção) é só de
 * {@link TemporizacaoEventoErrorInterceptor}; a desserialização é a única tradução feita aqui.
 */
@Component
public class TemporizacaoEventoListener {

    private final AgendarExpiracaoUseCase useCase;
    private final ObjectMapper objectMapper;

    public TemporizacaoEventoListener(AgendarExpiracaoUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @SqsListener(queueNames = "${sqs.queue-url}", factory = "temporizacaoSqsListenerContainerFactory")
    public void receber(String body) {
        // traceId por mensagem: MDC e por thread do pool, sempre limpo no finally.
        MDC.put("traceId", UUID.randomUUID().toString());
        try {
            AutorizacaoEventoPayload payload = desserializar(body);
            useCase.agendar(payload.idAutorizacao(), payload.dataHoraInclusao());
        } finally {
            MDC.clear();
        }
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
