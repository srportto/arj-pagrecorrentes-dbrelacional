package br.com.srportto.contratocommand.application.eventos;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.shared.config.AwsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do AutorizacaoEventoPublisher")
class AutorizacaoEventoPublisherTest {

    private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:000000000000:sns-estados-autorizacao";

    @Mock
    private SnsClient snsClient;

    private Autorizacao autorizacao() {
        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(new IdAutorizacao(UUID.randomUUID(), 950));
        aut.setTipoProduto(TipoProduto.PIX_AUTO);
        aut.setStatus(4);
        return aut;
    }

    @Test
    @DisplayName("publica no tópico configurado com o tipo do evento como message attribute")
    void publicaComMessageAttribute() {
        AwsProperties properties = new AwsProperties(null, "us-east-1", null, null, new AwsProperties.Sns(TOPIC_ARN));
        AutorizacaoEventoPublisher publisher = new AutorizacaoEventoPublisher(snsClient, properties);

        publisher.aoPersistir(new AutorizacaoPersistidaEvent(autorizacao(), TipoEventoAutorizacao.CRIACAO));

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());

        PublishRequest request = captor.getValue();
        assertEquals(TOPIC_ARN, request.topicArn());
        assertTrue(request.message().contains("id_autorizacao"));
        assertEquals("CRIACAO", request.messageAttributes().get("tipoEvento").stringValue());
    }

    @Test
    @DisplayName("falha ao publicar não propaga exceção (sem outbox: só loga)")
    void falhaAoPublicarNaoPropaga() {
        AwsProperties properties = new AwsProperties(null, "us-east-1", null, null, new AwsProperties.Sns(TOPIC_ARN));
        AutorizacaoEventoPublisher publisher = new AutorizacaoEventoPublisher(snsClient, properties);
        when(snsClient.publish(any(PublishRequest.class))).thenThrow(new RuntimeException("Floci fora do ar"));

        assertDoesNotThrow(() -> publisher.aoPersistir(
                new AutorizacaoPersistidaEvent(autorizacao(), TipoEventoAutorizacao.CANCELAMENTO)));
    }
}
