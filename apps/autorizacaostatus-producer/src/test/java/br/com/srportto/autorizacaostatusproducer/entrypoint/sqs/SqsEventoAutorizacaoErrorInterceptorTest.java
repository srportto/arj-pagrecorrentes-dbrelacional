package br.com.srportto.autorizacaostatusproducer.entrypoint.sqs;

import br.com.srportto.autorizacaostatusproducer.shared.exceptions.EventoAutorizacaoInvalidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.Message;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Testes do SqsEventoAutorizacaoErrorInterceptor")
class SqsEventoAutorizacaoErrorInterceptorTest {

    private final SqsEventoAutorizacaoErrorInterceptor interceptor = new SqsEventoAutorizacaoErrorInterceptor();

    private Message mensagem(String id) {
        return Message.builder().messageId(id).receiptHandle("receipt-" + id).body("{}").build();
    }

    @Test
    @DisplayName("EventoAutorizacaoInvalidoException é classificada como não-retryable (ack)")
    void eventoInvalidoEClassificadoComoNaoRetryable() {
        boolean deveDarAck = interceptor.tratar(mensagem("m1"),
                new EventoAutorizacaoInvalidoException("inválido", new RuntimeException()));

        assertTrue(deveDarAck);
    }

    @Test
    @DisplayName("Falha genérica (ex.: Kafka indisponível) é classificada como retryable (sem ack)")
    void falhaGenericaEClassificadaComoRetryable() {
        boolean deveDarAck = interceptor.tratar(mensagem("m2"), new RuntimeException("kafka fora do ar"));

        assertFalse(deveDarAck);
    }

}
