package br.com.srportto.autorizacaostatusproducer.infrastructure.messaging;

import br.com.srportto.autorizacaostatusproducer.domain.exception.EventoAutorizacaoInvalidoException;
import br.com.srportto.autorizacaostatusproducer.domain.model.EventoAutorizacao;
import br.com.srportto.autorizacaostatusproducer.domain.port.in.ProcessarEventoAutorizacaoUseCase;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Testes unitários (sem contexto Spring, sem fila real) da desserialização/validação/conversão
 * que D1 (design.md) moveu do caso de uso para este adaptador. Cobre empiricamente a task 5.5/5.6
 * — JSON malformado e campo obrigatório nulo continuam não-retryable — sem depender do Floci,
 * que não está disponível neste ambiente (ver relato da migração).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do SqsEventoAutorizacaoListener")
class SqsEventoAutorizacaoListenerTest {

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
    private ProcessarEventoAutorizacaoUseCase useCase;

    private final AutorizacaoEventoPayloadValidator validator = new AutorizacaoEventoPayloadValidator();
    private final EventoAutorizacaoConverter converter = new EventoAutorizacaoConverter();

    private SqsEventoAutorizacaoListener listener;

    private void inicializar() {
        listener = new SqsEventoAutorizacaoListener(useCase, validator, converter, new tools.jackson.databind.ObjectMapper());
    }

    /** Troca o valor do campo por null explícito — o caso que o builder Avro aceita em silêncio. */
    private String mensagemComNulo(String campoJson) {
        return MENSAGEM_VALIDA.replaceFirst(
                "\"" + campoJson + "\":(\"[^\"]*\"|[0-9]+)", "\"" + campoJson + "\":null");
    }

    @Test
    @DisplayName("processa com sucesso: desserializa, valida, converte e delega ao use case com o tipoEvento derivado")
    void processaComSucesso() {
        inicializar();

        assertDoesNotThrow(() -> listener.receber(MENSAGEM_VALIDA));

        verify(useCase).processar(any(EventoAutorizacao.class), any());
    }

    @Test
    @DisplayName("JSON malformado vira EventoAutorizacaoInvalidoException (não-retryable) — task 5.3/5.5")
    void jsonMalformadoViraExcecaoNaoRetryable() {
        inicializar();

        assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> listener.receber("{isso nao e json"));

        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("payload sem os campos obrigatórios do schema Avro vira EventoAutorizacaoInvalidoException")
    void payloadIncompletoViraExcecaoNaoRetryable() {
        inicializar();

        assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> listener.receber("{\"id_autorizacao\":\"550e8400-e29b-41d4-a716-446655440000\"}"));

        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("id_autorizacao nulo é classificado como inválido (task 5.6, campo obrigatório nulo)")
    void idAutorizacaoNuloNaoEscapaComoNpe() {
        inicializar();

        var e = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> listener.receber(mensagemComNulo("id_autorizacao")));

        assertTrue(e.getMessage().contains("id_autorizacao"));
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("codigo_canal_contratacao nulo é classificado antes do use case")
    void codigoCanalNuloNaoEscapaComoSerializationException() {
        inicializar();

        var e = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> listener.receber(mensagemComNulo("codigo_canal_contratacao")));

        assertTrue(e.getMessage().contains("codigo_canal_contratacao"));
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("nenhuma mensagem de erro do listener carrega o body do payload (PII fora do log)")
    void mensagensDeErroNaoCarregamOBody() {
        inicializar();

        var invalido = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> listener.receber(mensagemComNulo("id_autorizacao")));
        var statusRuim = assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> listener.receber(MENSAGEM_VALIDA.replace("\"status\":4", "\"status\":99")));

        assertFalse(invalido.getMessage().contains("id_pessoa"));
        assertFalse(invalido.getMessage().contains("{"));
        assertFalse(statusRuim.getMessage().contains("{"));
    }

    @Test
    @DisplayName("valor malformado em campo PII não vaza o conteúdo pela cadeia de causas")
    void valorMalformadoEmCampoPiiNaoVazaPelaCause() {
        inicializar();
        String cpfNoUuid = "12345678900";
        // 36 caracteres: cai no parse padrão do UUID, que embute o valor literal na mensagem
        String valorMalformado = "nao-e-um-uuid-valido-CPF-" + cpfNoUuid;
        String mensagem = MENSAGEM_VALIDA.replace("\"codigo_canal_contratacao\":\"canal\"",
                "\"codigo_canal_contratacao\":\"canal\",\"id_pessoa_pagadora\":\"" + valorMalformado + "\"");

        var e = assertThrows(EventoAutorizacaoInvalidoException.class, () -> listener.receber(mensagem));

        // nem a mensagem nem a cadeia de causas (impressa pelo log.error) podem carregar o conteúdo
        assertFalse(textoCompletoDe(e).contains(cpfNoUuid),
                "o conteúdo do campo PII vazou na exceção: " + textoCompletoDe(e));
        assertTrue(e.getMessage().contains("id_pessoa_pagadora"), "o caminho do campo deve ser diagnosticável");
    }

    @Test
    @DisplayName("decimal acima da precisão do schema é classificado antes do use case")
    void decimalAcimaDaPrecisaoNaoChegaAoProduce() {
        inicializar();
        String mensagem = MENSAGEM_VALIDA.replace("\"codigo_canal_contratacao\":\"canal\"",
                "\"codigo_canal_contratacao\":\"canal\",\"valor\":1000000000000000.00");

        var e = assertThrows(EventoAutorizacaoInvalidoException.class, () -> listener.receber(mensagem));

        assertTrue(e.getMessage().contains("valor"));
        verifyNoInteractions(useCase);
    }

    /** Junta mensagem e toda a cadeia de causas — é o que o log.error(..., e) do interceptor imprime. */
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
                () -> listener.receber(mensagemComStatusInvalido));

        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("propriedade desconhecida no payload é ignorada e a mensagem é processada normalmente")
    void propriedadeDesconhecidaEIgnorada() {
        inicializar();
        String mensagemComCampoNovo = MENSAGEM_VALIDA.replace(
                "\"codigo_canal_contratacao\":\"canal\"",
                "\"codigo_canal_contratacao\":\"canal\",\"campo_futuro_ainda_nao_replicado\":\"qualquer valor\"");

        assertDoesNotThrow(() -> listener.receber(mensagemComCampoNovo));

        verify(useCase).processar(any(EventoAutorizacao.class), any());
    }

    @Test
    @DisplayName("regressão: campo obrigatório ausente continua não-retryable mesmo com ignoreUnknown habilitado")
    void campoObrigatorioAusenteContinuaNaoRetryable() {
        inicializar();

        assertThrows(EventoAutorizacaoInvalidoException.class,
                () -> listener.receber("{\"campo_futuro_ainda_nao_replicado\":\"x\"}"));

        verifyNoInteractions(useCase);
    }

}
