package br.com.srportto.autorizacaostatusproducer.application.eventos;

import br.com.srportto.autorizacaostatusproducer.infrastructure.kafka.KafkaEventoAutorizacaoProducer;
import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Converte o evento consumido da fila para Avro e produz no tópico Kafka (ponte SQS -> Kafka). */
@Service
public class ProcessarEventoAutorizacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarEventoAutorizacaoUseCase.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventoAutorizacaoConverter converter;
    private final IdempotenciaKeyGenerator keyGenerator;
    private final KafkaEventoAutorizacaoProducer kafkaProducer;

    public ProcessarEventoAutorizacaoUseCase(EventoAutorizacaoConverter converter,
            IdempotenciaKeyGenerator keyGenerator, KafkaEventoAutorizacaoProducer kafkaProducer) {
        this.converter = converter;
        this.keyGenerator = keyGenerator;
        this.kafkaProducer = kafkaProducer;
    }

    public void processar(String mensagemJson) {
        AutorizacaoEventoPayload payload = desserializar(mensagemJson);
        TipoEventoAutorizacao tipoEvento = derivarTipoEvento(payload, mensagemJson);
        EventoAutorizacao evento = paraEventoAvro(payload, mensagemJson);
        String key = keyGenerator.gerar(payload.idAutorizacao(), payload.dataHoraUltimaAtualizacao());

        kafkaProducer.produzir(key, evento, tipoEvento.name());

        log.info("Autorização {} produzida com sucesso no tópico eventos-autorizacao (key={}, tipoEvento={}): {}",
                payload.idAutorizacao(), key, tipoEvento, mensagemJson);
    }

    private AutorizacaoEventoPayload desserializar(String mensagemJson) {
        try {
            return objectMapper.readValue(mensagemJson, AutorizacaoEventoPayload.class);
        } catch (RuntimeException e) {
            throw new EventoAutorizacaoInvalidoException(
                    "Body da mensagem não é um JSON válido: " + mensagemJson, e);
        }
    }

    private TipoEventoAutorizacao derivarTipoEvento(AutorizacaoEventoPayload payload, String mensagemJson) {
        try {
            return TipoEventoAutorizacao.porStatus(payload.status());
        } catch (RuntimeException e) {
            throw new EventoAutorizacaoInvalidoException(
                    "Status ausente, inválido ou desconhecido no payload: " + mensagemJson, e);
        }
    }

    private EventoAutorizacao paraEventoAvro(AutorizacaoEventoPayload payload, String mensagemJson) {
        try {
            return converter.converter(payload);
        } catch (RuntimeException e) {
            throw new EventoAutorizacaoInvalidoException(
                    "Falha ao converter o payload para o schema Avro: " + mensagemJson, e);
        }
    }

}
