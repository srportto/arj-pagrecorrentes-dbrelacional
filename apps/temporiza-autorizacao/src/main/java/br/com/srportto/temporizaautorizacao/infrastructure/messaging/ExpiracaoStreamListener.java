package br.com.srportto.temporizaautorizacao.infrastructure.messaging;

import br.com.srportto.temporizaautorizacao.domain.exception.ExpiracaoRetryavelException;
import br.com.srportto.temporizaautorizacao.domain.port.in.ProcessarExpiracaoUseCase;
import br.com.srportto.temporizaautorizacao.infrastructure.config.TemporizacaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consome o stream de expirações (ack manual via
 * {@link br.com.srportto.temporizaautorizacao.infrastructure.config.ValkeyStreamConfig}). Só
 * confirma (XACK) após {@link ProcessarExpiracaoUseCase} concluir sem exceção; falha retryable
 * ou inesperada deixa a entrada pendente, elegível a
 * {@link br.com.srportto.temporizaautorizacao.infrastructure.scheduler.PendenciasSchedulerReivindicador}.
 */
@Component
public class ExpiracaoStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(ExpiracaoStreamListener.class);
    /** Compartilhada com {@code PendenciasSchedulerReivindicador} e o {@code @JsonProperty} de {@link AutorizacaoEventoPayload}. */
    public static final String CAMPO_ID_AUTORIZACAO = "id_autorizacao";

    private final ProcessarExpiracaoUseCase processarExpiracaoUseCase;
    private final StringRedisTemplate redisTemplate;
    private final TemporizacaoProperties properties;

    public ExpiracaoStreamListener(ProcessarExpiracaoUseCase processarExpiracaoUseCase,
            StringRedisTemplate redisTemplate, TemporizacaoProperties properties) {
        this.processarExpiracaoUseCase = processarExpiracaoUseCase;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        processarEConfirmarSeConcluido(message.getId(), message.getValue().get(CAMPO_ID_AUTORIZACAO));
    }

    /**
     * Reusado por {@code PendenciasSchedulerReivindicador} p/ reprocessar entradas reivindicadas.
     * O parse de {@code String} para {@link UUID} é responsabilidade do adaptador — a porta
     * {@link ProcessarExpiracaoUseCase} recebe o identificador já tipado.
     */
    public void processarEConfirmarSeConcluido(RecordId streamId, String idAutorizacaoStr) {
        // traceId por mensagem: o streamId já é o identificador único da entrada (equivalente ao
        // atributo de correlação de outras filas); cobre também o reprocessamento via
        // PendenciasSchedulerReivindicador, que chama este mesmo método. MDC e por thread, limpo no finally.
        MDC.put("traceId", streamId.getValue());
        try {
            UUID idAutorizacao = UUID.fromString(idAutorizacaoStr);
            processarExpiracaoUseCase.processar(idAutorizacao);
            confirmar(streamId);
        } catch (ExpiracaoRetryavelException e) {
            log.error("Falha retryable ao processar expiração de {}, entrada permanece pendente: streamId={}",
                    idAutorizacaoStr, streamId, e);
        } catch (RuntimeException e) {
            // inclui IllegalArgumentException de UUID.fromString malformado — classificação
            // explícita em vez de deixar escapar sem log nem decisão de ack/retenção.
            log.error("Falha inesperada ao processar expiração de {}, entrada permanece pendente: streamId={}",
                    idAutorizacaoStr, streamId, e);
        } finally {
            MDC.clear();
        }
    }

    private void confirmar(RecordId streamId) {
        redisTemplate.opsForStream().acknowledge(properties.chaveStream(), properties.grupoConsumidor(), streamId);
    }

}
