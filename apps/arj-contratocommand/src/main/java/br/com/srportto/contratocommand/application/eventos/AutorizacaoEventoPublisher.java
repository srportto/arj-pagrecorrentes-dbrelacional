package br.com.srportto.contratocommand.application.eventos;

import br.com.srportto.contratocommand.shared.config.AwsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/** Publica no SNS a autorização persistida após o commit; falha de publish só é logada (ver design.md). */
@Component
public class AutorizacaoEventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(AutorizacaoEventoPublisher.class);
    private static final String MESSAGE_ATTRIBUTE_TIPO_EVENTO = "tipoEvento";
    private static final String MESSAGE_ATTRIBUTE_TIPO_PRODUTO = "tipoProduto";
    private static final String MESSAGE_ATTRIBUTE_TIPO_JORNADA = "tipoJornada";

    private final SnsClient snsClient;
    private final AwsProperties awsProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AutorizacaoEventoPublisher(SnsClient snsClient, AwsProperties awsProperties) {
        this.snsClient = snsClient;
        this.awsProperties = awsProperties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoPersistir(AutorizacaoPersistidaEvent evento) {
        var payload = AutorizacaoEventoPayload.from(evento.autorizacao());
        var tipoEvento = TipoEventoAutorizacao.porStatus(evento.autorizacao().getStatus());

        try {
            var mensagem = objectMapper.writeValueAsString(payload);

            snsClient.publish(PublishRequest.builder()
                    .topicArn(awsProperties.sns().topicArn())
                    .message(mensagem)
                    .messageAttributes(Map.of(
                            MESSAGE_ATTRIBUTE_TIPO_EVENTO, stringAttribute(tipoEvento.name()),
                            MESSAGE_ATTRIBUTE_TIPO_PRODUTO, stringAttribute(evento.autorizacao().getTipoProduto().name()),
                            MESSAGE_ATTRIBUTE_TIPO_JORNADA, stringAttribute(evento.autorizacao().getTipoJornada().name())))
                    .build());
        } catch (Exception e) {
            log.error("Falha ao publicar evento {} da autorização {} no tópico SNS",
                    tipoEvento, payload.idAutorizacao(), e);
        }
    }

    private static MessageAttributeValue stringAttribute(String valor) {
        return MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(valor)
                .build();
    }

}
