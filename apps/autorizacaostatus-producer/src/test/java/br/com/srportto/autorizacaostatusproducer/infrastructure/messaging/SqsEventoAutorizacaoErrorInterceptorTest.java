package br.com.srportto.autorizacaostatusproducer.infrastructure.messaging;

import br.com.srportto.autorizacaostatusproducer.domain.exception.EventoAutorizacaoInvalidoException;
import br.com.srportto.autorizacaostatusproducer.domain.exception.EventoAutorizacaoKafkaIndisponivelException;
import io.awspring.cloud.sqs.listener.ListenerExecutionFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Testes do SqsEventoAutorizacaoErrorInterceptor")
class SqsEventoAutorizacaoErrorInterceptorTest {

    private final SqsEventoAutorizacaoErrorInterceptor interceptor = new SqsEventoAutorizacaoErrorInterceptor();

    private final Message<String> mensagem = new GenericMessage<>("{}");

    @Test
    @DisplayName("EventoAutorizacaoInvalidoException direta é engolida — container interpreta como recuperada e acka")
    void eventoInvalidoDiretoEEngolido() {
        assertDoesNotThrow(() -> interceptor.handle(mensagem,
                new EventoAutorizacaoInvalidoException("inválido", new RuntimeException())));
    }

    @Test
    @DisplayName("EventoAutorizacaoInvalidoException envolta em ListenerExecutionFailedException também é engolida")
    void eventoInvalidoEnvoltoEEngolido() {
        var envelope = new ListenerExecutionFailedException("falha no listener",
                new EventoAutorizacaoInvalidoException("inválido", new RuntimeException()), mensagem);

        assertDoesNotThrow(() -> interceptor.handle(mensagem, envelope));
    }

    @Test
    @DisplayName("Falha retryable (ex.: Kafka indisponível) é relançada — mensagem não é confirmada")
    void falhaRetryableERelancada() {
        assertThrows(EventoAutorizacaoKafkaIndisponivelException.class,
                () -> interceptor.handle(mensagem,
                        new EventoAutorizacaoKafkaIndisponivelException("kafka fora do ar", new RuntimeException())));
    }

    @Test
    @DisplayName("Exceção checked é relançada envolta em RuntimeException — o framework exige um throw de RuntimeException")
    void excecaoCheckedERelancadaEnvolta() {
        assertThrows(IllegalStateException.class,
                () -> interceptor.handle(mensagem, new Exception("falha checked")));
    }

    @Test
    @DisplayName("cadeia de causas cíclica não trava a classificação")
    void cadeiaCiclicaNaoTrava() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);

        assertThrows(RuntimeException.class, () -> interceptor.handle(mensagem, b));
    }

}
