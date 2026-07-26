package br.com.srportto.autorizacaostatusproducer.application.eventos;

import br.com.srportto.autorizacaostatusproducer.infrastructure.kafka.EventoAutorizacaoKafkaIndisponivelException;
import br.com.srportto.autorizacaostatusproducer.infrastructure.kafka.KafkaEventoAutorizacaoProducer;
import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do ProcessarEventoAutorizacaoUseCase")
class ProcessarEventoAutorizacaoUseCaseTest {

    private static final String MENSAGEM_VALIDA = "{"
            + "\"id_autorizacao\":\"550e8400-e29b-41d4-a716-446655440000\","
            + "\"id_particao_conta\":950,"
            + "\"data_fim_vigencia\":\"2027-01-01\","
            + "\"tipo_produto\":1,"
            + "\"status\":4,"
            + "\"data_hora_inclusao\":\"2026-07-26T10:00:00\","
            + "\"data_hora_ultima_atlz\":\"2026-07-26T10:00:00\","
            + "\"codigo_canal_contratacao\":\"canal\"}";

    @Mock
    private KafkaEventoAutorizacaoProducer kafkaProducer;

    private final EventoAutorizacaoConverter converter = new EventoAutorizacaoConverter();
    private final IdempotenciaKeyGenerator keyGenerator = new IdempotenciaKeyGenerator();

    private ProcessarEventoAutorizacaoUseCase useCase;

    private void inicializar() {
        useCase = new ProcessarEventoAutorizacaoUseCase(converter, keyGenerator, kafkaProducer);
    }

    @Test
    @DisplayName("processa com sucesso: converte o payload, deriva o tipoEvento do status e produz no Kafka")
    void processaComSucesso() {
        inicializar();

        assertDoesNotThrow(() -> useCase.processar(MENSAGEM_VALIDA));

        verify(kafkaProducer).produzir(anyString(), any(EventoAutorizacao.class), eq("ATIVACAO"));
    }

    @Test
    @DisplayName("JSON malformado vira EventoAutorizacaoInvalidoException (não-retryable)")
    void jsonMalformadoViraExcecaoNaoRetryable() {
        inicializar();

        assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> useCase.processar("{isso nao e json"));
    }

    @Test
    @DisplayName("payload sem os campos obrigatórios do schema Avro vira EventoAutorizacaoInvalidoException")
    void payloadIncompletoViraExcecaoNaoRetryable() {
        inicializar();

        assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> useCase.processar("{\"id_autorizacao\":\"550e8400-e29b-41d4-a716-446655440000\"}"));
    }

    @Test
    @DisplayName("status desconhecido no payload vira EventoAutorizacaoInvalidoException")
    void statusDesconhecidoViraExcecaoNaoRetryable() {
        inicializar();
        String mensagemComStatusInvalido = MENSAGEM_VALIDA.replace("\"status\":4", "\"status\":99");

        assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> useCase.processar(mensagemComStatusInvalido));
    }

    @Test
    @DisplayName("falha do Kafka propaga sem tratamento (retryable)")
    void falhaDoKafkaPropaga() {
        inicializar();
        doThrow(new EventoAutorizacaoKafkaIndisponivelException("indisponível", new RuntimeException()))
                .when(kafkaProducer).produzir(anyString(), any(EventoAutorizacao.class), anyString());

        assertThrows(EventoAutorizacaoKafkaIndisponivelException.class,
                () -> useCase.processar(MENSAGEM_VALIDA));
    }

}
