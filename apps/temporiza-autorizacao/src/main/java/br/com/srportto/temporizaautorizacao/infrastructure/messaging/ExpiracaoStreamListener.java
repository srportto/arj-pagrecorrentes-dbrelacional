package br.com.srportto.temporizaautorizacao.infrastructure.messaging;

import br.com.srportto.temporizaautorizacao.domain.exception.ExpiracaoRetryavelException;
import br.com.srportto.temporizaautorizacao.domain.port.in.ProcessarExpiracaoUseCase;
import br.com.srportto.temporizaautorizacao.infrastructure.config.TemporizacaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

/**
 * Consome o stream de expirações (ack manual via
 * {@link br.com.srportto.temporizaautorizacao.infrastructure.config.ValkeyStreamConfig}). Só
 * confirma (XACK) após {@link ProcessarExpiracaoUseCase} concluir sem exceção; falha retryable
 * deixa a entrada pendente, elegível a {@link PendenciasSchedulerReivindicador}.
 */
@Component
public class ExpiracaoStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(ExpiracaoStreamListener.class);
    private static final String CAMPO_ID_AUTORIZACAO = "id_autorizacao";

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

    /** Reusado por {@link PendenciasSchedulerReivindicador} p/ reprocessar entradas reivindicadas. */
    public void processarEConfirmarSeConcluido(RecordId streamId, String idAutorizacao) {
        try {
            processarExpiracaoUseCase.processar(idAutorizacao);
            confirmar(streamId);
        } catch (ExpiracaoRetryavelException e) {
            log.error("Falha retryable ao processar expiração de {}, entrada permanece pendente: streamId={}",
                    idAutorizacao, streamId, e);
        }
    }

    private void confirmar(RecordId streamId) {
        redisTemplate.opsForStream().acknowledge(properties.chaveStream(), properties.grupoConsumidor(), streamId);
    }

}
