package br.com.srportto.temporizaautorizacao.infrastructure.messaging;

import br.com.srportto.temporizaautorizacao.domain.exception.ExpiracaoRetryavelException;
import br.com.srportto.temporizaautorizacao.domain.port.in.ProcessarExpiracaoUseCase;
import br.com.srportto.temporizaautorizacao.infrastructure.config.TemporizacaoProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
@DisplayName("Testes do ExpiracaoStreamListener")
class ExpiracaoStreamListenerTest {

    @Mock
    private ProcessarExpiracaoUseCase processarExpiracaoUseCase;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    private final TemporizacaoProperties properties = new TemporizacaoProperties(
            10, 5000, 100, 120000, "agenda:{pixauto:j1}", "stream:{pixauto:j1}:expiracoes",
            "temporizaautorizacao", "worker-1", "http://localhost:8080", 5000, 600000);

    private ExpiracaoStreamListener listener;

    private MapRecord<String, String, String> mensagem(UUID id) {
        return StreamRecords.newRecord()
                .in(properties.chaveStream())
                .withId(RecordId.of("1-0"))
                .ofMap(Map.of("id_autorizacao", id.toString()));
    }

    @Test
    @DisplayName("processamento com sucesso confirma (XACK) a entrada")
    void processamentoComSucessoConfirma() {
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
        listener = new ExpiracaoStreamListener(processarExpiracaoUseCase, redisTemplate, properties);

        var id = UUID.randomUUID();
        listener.onMessage(mensagem(id));

        verify(processarExpiracaoUseCase).processar(id);
        verify(streamOperations).acknowledge(eq(properties.chaveStream()), eq(properties.grupoConsumidor()), any(RecordId.class));
    }

    @Test
    @DisplayName("falha retryável NÃO confirma a entrada e não propaga a exceção")
    void falhaRetryavelNaoConfirma() {
        listener = new ExpiracaoStreamListener(processarExpiracaoUseCase, redisTemplate, properties);

        var id = UUID.randomUUID();
        doThrow(new ExpiracaoRetryavelException("falha", new RuntimeException()))
                .when(processarExpiracaoUseCase).processar(id);

        listener.onMessage(mensagem(id));

        verify(redisTemplate, never()).opsForStream();
    }

    @AfterEach
    void limpaMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("MDC é limpo após o processamento, com sucesso ou falha")
    void mdcELimpoAposProcessamento() {
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
        listener = new ExpiracaoStreamListener(processarExpiracaoUseCase, redisTemplate, properties);

        listener.onMessage(mensagem(UUID.randomUUID()));
        assertNull(MDC.get("traceId"), "MDC deve ser limpo após sucesso");

        var idFalha = UUID.randomUUID();
        doThrow(new ExpiracaoRetryavelException("falha", new RuntimeException()))
                .when(processarExpiracaoUseCase).processar(idFalha);
        listener.onMessage(mensagem(idFalha));
        assertNull(MDC.get("traceId"), "MDC deve ser limpo mesmo após falha retryable");
    }

    @Test
    @DisplayName("um traceId (streamId) é populado no MDC durante o processamento da mensagem")
    void traceIdEPopuladoDuranteOProcessamento() {
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
        listener = new ExpiracaoStreamListener(processarExpiracaoUseCase, redisTemplate, properties);
        var id = UUID.randomUUID();
        doAnswer(invocation -> {
            assertTrue(MDC.get("traceId") != null && !MDC.get("traceId").isBlank());
            return null;
        }).when(processarExpiracaoUseCase).processar(id);

        listener.onMessage(mensagem(id));

        verify(processarExpiracaoUseCase).processar(id);
    }

}
