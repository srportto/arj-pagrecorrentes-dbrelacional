package br.com.srportto.temporizaautorizacao.entrypoint.sqs;

import br.com.srportto.temporizaautorizacao.shared.exceptions.AgendamentoInvalidoException;
import br.com.srportto.temporizaautorizacao.shared.exceptions.ExpiracaoRetryavelException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Testes do TemporizacaoEventoErrorInterceptor")
class TemporizacaoEventoErrorInterceptorTest {

    private final TemporizacaoEventoErrorInterceptor interceptor = new TemporizacaoEventoErrorInterceptor();

    private final Message<String> mensagem = new GenericMessage<>("{}");

    @Test
    @DisplayName("AgendamentoInvalidoException é engolida — container interpreta como recuperada e acka")
    void agendamentoInvalidoEEngolido() {
        assertDoesNotThrow(() -> interceptor.handle(mensagem,
                new AgendamentoInvalidoException("payload sem campos obrigatórios")));
    }

    @Test
    @DisplayName("falha retryable (ex.: infraestrutura indisponível) é relançada — mensagem não é confirmada")
    void falhaRetryavelERelancada() {
        assertThrows(ExpiracaoRetryavelException.class,
                () -> interceptor.handle(mensagem,
                        new ExpiracaoRetryavelException("falha", new RuntimeException())));
    }

    @Test
    @DisplayName("exceção checked é relançada envolta em RuntimeException")
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
