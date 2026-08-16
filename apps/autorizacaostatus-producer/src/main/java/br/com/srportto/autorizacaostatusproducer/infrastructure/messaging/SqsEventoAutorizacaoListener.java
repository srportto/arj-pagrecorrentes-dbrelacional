package br.com.srportto.autorizacaostatusproducer.infrastructure.messaging;

import br.com.srportto.autorizacaostatusproducer.domain.enums.TipoEventoAutorizacao;
import br.com.srportto.autorizacaostatusproducer.domain.exception.EventoAutorizacaoInvalidoException;
import br.com.srportto.autorizacaostatusproducer.domain.model.EventoAutorizacao;
import br.com.srportto.autorizacaostatusproducer.domain.port.in.ProcessarEventoAutorizacaoUseCase;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador de ENTRADA: consome a fila via {@code @SqsListener}, desserializa, valida e
 * converte o payload para o modelo de domínio, e delega ao use case já com o evento tipado
 * (D1 — a tradução de formato de fio é responsabilidade do adaptador, não do caso de uso).
 *
 * <p>Ack não é explícito: retorno normal = container confirma a mensagem. Como o use
 * case produz no Kafka de forma síncrona antes de retornar, o ack fica condicionado à
 * confirmação do broker (at-least-once).
 *
 * <p>Sem {@code try/catch} de falha de negócio: classificação é responsabilidade
 * exclusiva de {@link SqsEventoAutorizacaoErrorInterceptor} — duplicar aqui reintroduz
 * o espalhamento que o interceptor existe para evitar. A desserialização/validação é a
 * única tradução feita aqui, e suas falhas continuam chegando ao interceptor como
 * {@link EventoAutorizacaoInvalidoException} (não-retryável).
 */
@Component
public class SqsEventoAutorizacaoListener {

    private final ProcessarEventoAutorizacaoUseCase useCase;
    private final AutorizacaoEventoPayloadValidator validator;
    private final EventoAutorizacaoConverter converter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SqsEventoAutorizacaoListener(ProcessarEventoAutorizacaoUseCase useCase,
            AutorizacaoEventoPayloadValidator validator, EventoAutorizacaoConverter converter) {
        this.useCase = useCase;
        this.validator = validator;
        this.converter = converter;
    }

    @SqsListener(queueNames = "${sqs.queue-url}", factory = "eventosAutorizacaoSqsListenerContainerFactory")
    public void receber(String body) {
        AutorizacaoEventoPayload payload = desserializar(body);

        // valida antes: campo obrigatório nulo passa em silêncio pelo builder Avro
        validator.validar(payload);

        TipoEventoAutorizacao tipoEvento = derivarTipoEvento(payload);
        EventoAutorizacao evento = paraEventoDominio(payload);

        useCase.processar(evento, tipoEvento);
    }

    /**
     * Não propaga a exceção do Jackson como {@code cause}: a mensagem de erro de coerção
     * embute o <em>valor</em> do campo (PII em id_pessoa_* e valor). {@code getPathReference()}
     * dá o caminho sem o valor.
     */
    private AutorizacaoEventoPayload desserializar(String mensagemJson) {
        try {
            return objectMapper.readValue(mensagemJson, AutorizacaoEventoPayload.class);
        } catch (JacksonException e) {
            throw new EventoAutorizacaoInvalidoException(
                    "Campo do payload não pôde ser convertido para o tipo esperado: campo="
                            + e.getPathReference() + " causa=" + e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            throw new EventoAutorizacaoInvalidoException(
                    "Body da mensagem não é um JSON válido para o payload de autorização ("
                            + e.getClass().getSimpleName() + ")");
        }
    }

    private TipoEventoAutorizacao derivarTipoEvento(AutorizacaoEventoPayload payload) {
        try {
            return TipoEventoAutorizacao.porStatus(payload.status());
        } catch (RuntimeException e) {
            throw new EventoAutorizacaoInvalidoException(
                    "Status desconhecido no payload: " + payload.status(), e);
        }
    }

    private EventoAutorizacao paraEventoDominio(AutorizacaoEventoPayload payload) {
        try {
            return converter.converter(payload);
        } catch (RuntimeException e) {
            // sem cause: a exceção de conversão pode embutir o valor do campo rejeitado
            throw new EventoAutorizacaoInvalidoException(
                    "Falha ao converter o payload para o modelo de domínio ("
                            + e.getClass().getSimpleName() + ")");
        }
    }

}
