package br.com.srportto.autorizacaostatusproducer.application.eventos;

import br.com.srportto.autorizacaostatusproducer.shared.exceptions.EventoAutorizacaoInvalidoException;
import br.com.srportto.autorizacaostatusproducer.domain.enums.TipoEventoAutorizacao;
import br.com.srportto.autorizacaostatusproducer.shared.exceptions.EventoAutorizacaoKafkaIndisponivelException;
import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
    private PublicadorEventoAutorizacao publicador;

    private final AutorizacaoEventoPayloadValidator validator = new AutorizacaoEventoPayloadValidator();
    private final EventoAutorizacaoConverter converter = new EventoAutorizacaoConverter();
    private final IdempotenciaKeyGenerator keyGenerator = new IdempotenciaKeyGenerator();

    private ProcessarEventoAutorizacaoUseCase useCase;

    private void inicializar() {
        useCase = new ProcessarEventoAutorizacaoUseCase(validator, converter, keyGenerator, publicador);
    }

    /** Troca o valor do campo por null explícito — o caso que o builder Avro aceita em silêncio. */
    private String mensagemComNulo(String campoJson) {
        return MENSAGEM_VALIDA.replaceFirst(
                "\"" + campoJson + "\":(\"[^\"]*\"|[0-9]+)", "\"" + campoJson + "\":null");
    }

    @Test
    @DisplayName("processa com sucesso: converte o payload, deriva o tipoEvento do status e produz no Kafka")
    void processaComSucesso() {
        inicializar();

        assertDoesNotThrow(() -> useCase.processar(MENSAGEM_VALIDA));

        verify(publicador).produzir(anyString(), any(EventoAutorizacao.class), eq("ATIVACAO"));
    }

    @Test
    @DisplayName("JSON malformado vira EventoAutorizacaoInvalidoException (não-retryable)")
    void jsonMalformadoViraExcecaoNaoRetryable() {
        inicializar();

        assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> useCase.processar("{isso nao e json"));

        verifyNoInteractions(publicador);
    }

    @Test
    @DisplayName("payload sem os campos obrigatórios do schema Avro vira EventoAutorizacaoInvalidoException")
    void payloadIncompletoViraExcecaoNaoRetryable() {
        inicializar();

        assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> useCase.processar("{\"id_autorizacao\":\"550e8400-e29b-41d4-a716-446655440000\"}"));

        verifyNoInteractions(publicador);
    }

    @Test
    @DisplayName("id_autorizacao nulo é classificado como inválido antes de gerar a key (não vira NPE/reentrega infinita)")
    void idAutorizacaoNuloNaoEscapaComoNpe() {
        inicializar();

        var e = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> useCase.processar(mensagemComNulo("id_autorizacao")));

        assertTrue(e.getMessage().contains("id_autorizacao"));
        verify(publicador, never()).produzir(anyString(), any(EventoAutorizacao.class), anyString());
    }

    @Test
    @DisplayName("codigo_canal_contratacao nulo é classificado antes do send (não vira SerializationException síncrona)")
    void codigoCanalNuloNaoEscapaComoSerializationException() {
        inicializar();

        var e = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> useCase.processar(mensagemComNulo("codigo_canal_contratacao")));

        assertTrue(e.getMessage().contains("codigo_canal_contratacao"));
        verify(publicador, never()).produzir(anyString(), any(EventoAutorizacao.class), anyString());
    }

    @Test
    @DisplayName("nenhuma mensagem de erro do use case carrega o body do payload (PII fora do log)")
    void mensagensDeErroNaoCarregamOBody() {
        inicializar();

        var invalido = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> useCase.processar(mensagemComNulo("id_autorizacao")));
        var statusRuim = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> useCase.processar(MENSAGEM_VALIDA.replace("\"status\":4", "\"status\":99")));

        assertFalse(invalido.getMessage().contains("id_pessoa"));
        assertFalse(invalido.getMessage().contains("{"));
        assertFalse(statusRuim.getMessage().contains("{"));
    }

    @Test
    @DisplayName("valor malformado em campo PII não vaza o conteúdo pela cadeia de causas")
    void valorMalformadoEmCampoPiiNaoVazaPelaCause() {
        inicializar();
        String cpfNoUuid = "12345678900";
        // 36 caracteres: cai no caminho de parse padrao do UUID, que lanca embutindo o valor
        // literal na mensagem. Comprimentos diferentes seguem outro caminho no Jackson.
        String valorMalformado = "nao-e-um-uuid-valido-CPF-" + cpfNoUuid;
        String mensagem = MENSAGEM_VALIDA.replace("\"codigo_canal_contratacao\":\"canal\"",
                "\"codigo_canal_contratacao\":\"canal\",\"id_pessoa_pagadora\":\"" + valorMalformado + "\"");

        var e = assertThrows(EventoAutorizacaoInvalidoException.class, () -> useCase.processar(mensagem));

        // a mensagem do Jackson embutiria o valor literal do campo; nem ela nem a cadeia de
        // causas (impressa pelo log.error do listener) podem carregar o conteudo
        assertFalse(textoCompletoDe(e).contains(cpfNoUuid),
                "o conteúdo do campo PII vazou na exceção: " + textoCompletoDe(e));
        assertTrue(e.getMessage().contains("id_pessoa_pagadora"), "o caminho do campo deve ser diagnosticável");
    }

    @Test
    @DisplayName("decimal acima da precisão do schema é classificado antes do produce")
    void decimalAcimaDaPrecisaoNaoChegaAoProduce() {
        inicializar();
        String mensagem = MENSAGEM_VALIDA.replace("\"codigo_canal_contratacao\":\"canal\"",
                "\"codigo_canal_contratacao\":\"canal\",\"valor\":1000000000000000.00");

        var e = assertThrows(EventoAutorizacaoInvalidoException.class, () -> useCase.processar(mensagem));

        assertTrue(e.getMessage().contains("valor"));
        verify(publicador, never()).produzir(anyString(), any(EventoAutorizacao.class), anyString());
    }

    /** Junta mensagem e toda a cadeia de causas — é o que o log.error(..., e) do listener imprime. */
    private String textoCompletoDe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable atual = t; atual != null; atual = atual.getCause()) {
            sb.append(atual.getClass().getName()).append(':').append(atual.getMessage()).append('\n');
        }
        return sb.toString();
    }

    @Test
    @DisplayName("status desconhecido no payload vira EventoAutorizacaoInvalidoException")
    void statusDesconhecidoViraExcecaoNaoRetryable() {
        inicializar();
        String mensagemComStatusInvalido = MENSAGEM_VALIDA.replace("\"status\":4", "\"status\":99");

        assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> useCase.processar(mensagemComStatusInvalido));

        verifyNoInteractions(publicador);
    }

    @Test
    @DisplayName("propriedade desconhecida no payload é ignorada e a mensagem é processada normalmente")
    void propriedadeDesconhecidaEIgnorada() {
        inicializar();
        String mensagemComCampoNovo = MENSAGEM_VALIDA.replace(
                "\"codigo_canal_contratacao\":\"canal\"",
                "\"codigo_canal_contratacao\":\"canal\",\"campo_futuro_ainda_nao_replicado\":\"qualquer valor\"");

        assertDoesNotThrow(() -> useCase.processar(mensagemComCampoNovo));

        verify(publicador).produzir(anyString(), any(EventoAutorizacao.class), eq("ATIVACAO"));
    }

    @Test
    @DisplayName("regressão: campo obrigatório ausente continua não-retryable mesmo com ignoreUnknown habilitado")
    void campoObrigatorioAusenteContinuaNaoRetryable() {
        inicializar();

        assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> useCase.processar("{\"campo_futuro_ainda_nao_replicado\":\"x\"}"));

        verifyNoInteractions(publicador);
    }

    @Test
    @DisplayName("falha do Kafka propaga sem tratamento (retryable)")
    void falhaDoKafkaPropaga() {
        inicializar();
        doThrow(new EventoAutorizacaoKafkaIndisponivelException("indisponível", new RuntimeException()))
                .when(publicador).produzir(anyString(), any(EventoAutorizacao.class), anyString());

        assertThrows(EventoAutorizacaoKafkaIndisponivelException.class,
                () -> useCase.processar(MENSAGEM_VALIDA));
    }

}
