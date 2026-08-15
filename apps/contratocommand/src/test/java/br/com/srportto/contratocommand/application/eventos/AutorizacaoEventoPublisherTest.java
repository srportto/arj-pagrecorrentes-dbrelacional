package br.com.srportto.contratocommand.application.eventos;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.event.AutorizacaoPersistidaEvent;
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

    private Autorizacao autorizacao(TipoProduto tipoProduto, int status) {
        return autorizacao(tipoProduto, status, TipoJornadaAutorizacao.SPI_J1);
    }

    private Autorizacao autorizacao(TipoProduto tipoProduto, int status, TipoJornadaAutorizacao tipoJornada) {
        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(new IdAutorizacao(UUID.randomUUID(), 950));
        aut.setTipoProduto(tipoProduto);
        aut.setTipoJornada(tipoJornada);
        aut.setStatus(status);
        return aut;
    }

    @Test
    @DisplayName("publica no tópico com o tipo do evento derivado do status como message attribute (DDA_AUTO/ATIVA)")
    void publicaComMessageAttribute() {
        AwsProperties properties = new AwsProperties(null, "us-east-1", null, null, new AwsProperties.Sns(TOPIC_ARN));
        AutorizacaoEventoPublisher publisher = new AutorizacaoEventoPublisher(snsClient, properties);

        publisher.aoPersistir(new AutorizacaoPersistidaEvent(autorizacao(TipoProduto.DDA_AUTO, 4)));

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());

        PublishRequest request = captor.getValue();
        assertEquals(TOPIC_ARN, request.topicArn());
        assertTrue(request.message().contains("id_autorizacao"));
        assertEquals("ATIVACAO", request.messageAttributes().get("tipoEvento").stringValue());
        assertEquals("DDA_AUTO", request.messageAttributes().get("tipoProduto").stringValue());
        assertEquals("SPI_J1", request.messageAttributes().get("tipoJornada").stringValue());
    }

    @Test
    @DisplayName("aprovação (ATIVA) publica tipoEvento=ATIVACAO")
    void publicaAtivacaoParaAprovacao() {
        AwsProperties properties = new AwsProperties(null, "us-east-1", null, null, new AwsProperties.Sns(TOPIC_ARN));
        AutorizacaoEventoPublisher publisher = new AutorizacaoEventoPublisher(snsClient, properties);

        publisher.aoPersistir(new AutorizacaoPersistidaEvent(autorizacao(TipoProduto.PIX_AUTO, 4)));

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        assertEquals("ATIVACAO", captor.getValue().messageAttributes().get("tipoEvento").stringValue());
    }

    @Test
    @DisplayName("rejeição e expiração (REJEITADA) publicam tipoEvento=REJEICAO")
    void publicaRejeicaoParaRejeicaoEExpiracao() {
        AwsProperties properties = new AwsProperties(null, "us-east-1", null, null, new AwsProperties.Sns(TOPIC_ARN));
        AutorizacaoEventoPublisher publisher = new AutorizacaoEventoPublisher(snsClient, properties);

        publisher.aoPersistir(new AutorizacaoPersistidaEvent(autorizacao(TipoProduto.PIX_AUTO, 6)));

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        assertEquals("REJEICAO", captor.getValue().messageAttributes().get("tipoEvento").stringValue());
    }

    @Test
    @DisplayName("publica no tópico com tipoEvento=RECEPCAO para criação de PIX_AUTO (status RECEBIDA)")
    void publicaRecepcaoParaPixAutoRecebida() {
        AwsProperties properties = new AwsProperties(null, "us-east-1", null, null, new AwsProperties.Sns(TOPIC_ARN));
        AutorizacaoEventoPublisher publisher = new AutorizacaoEventoPublisher(snsClient, properties);

        publisher.aoPersistir(new AutorizacaoPersistidaEvent(autorizacao(TipoProduto.PIX_AUTO, 1)));

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());

        PublishRequest request = captor.getValue();
        assertEquals("RECEPCAO", request.messageAttributes().get("tipoEvento").stringValue());
        assertEquals("PIX_AUTO", request.messageAttributes().get("tipoProduto").stringValue());
        assertEquals("SPI_J1", request.messageAttributes().get("tipoJornada").stringValue());
    }

    @Test
    @DisplayName("falha ao publicar não propaga exceção (sem outbox: só loga)")
    void falhaAoPublicarNaoPropaga() {
        AwsProperties properties = new AwsProperties(null, "us-east-1", null, null, new AwsProperties.Sns(TOPIC_ARN));
        AutorizacaoEventoPublisher publisher = new AutorizacaoEventoPublisher(snsClient, properties);
        when(snsClient.publish(any(PublishRequest.class))).thenThrow(new RuntimeException("Floci fora do ar"));

        assertDoesNotThrow(() -> publisher.aoPersistir(new AutorizacaoPersistidaEvent(autorizacao(TipoProduto.DDA_AUTO, 4))));
    }
}
