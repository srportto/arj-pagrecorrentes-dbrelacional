package br.com.srportto.autorizacaostatusproducer.application.eventos;

import br.com.srportto.autorizacaostatusproducer.shared.exceptions.EventoAutorizacaoInvalidoException;
import br.com.srportto.autorizacaostatusproducer.domain.enums.TipoEventoAutorizacao;
import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Converte o evento consumido da fila para Avro e produz no tópico Kafka (ponte SQS -> Kafka).
 *
 * <p>Nenhum log nem mensagem de exceção daqui carrega o body do payload: ele contém dado
 * pessoal (id_pessoa_pagadora/devedora/recebedora, valor, descricao, metadados). A mensagem
 * é identificada por idAutorizacao, key e tipoEvento.
 */
@Service
public class ProcessarEventoAutorizacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarEventoAutorizacaoUseCase.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AutorizacaoEventoPayloadValidator validator;
    private final EventoAutorizacaoConverter converter;
    private final IdempotenciaKeyGenerator keyGenerator;
    private final PublicadorEventoAutorizacao publicador;

    public ProcessarEventoAutorizacaoUseCase(AutorizacaoEventoPayloadValidator validator,
            EventoAutorizacaoConverter converter, IdempotenciaKeyGenerator keyGenerator,
            PublicadorEventoAutorizacao publicador) {
        this.validator = validator;
        this.converter = converter;
        this.keyGenerator = keyGenerator;
        this.publicador = publicador;
    }

    public void processar(String mensagemJson) {
        AutorizacaoEventoPayload payload = desserializar(mensagemJson);

        // valida antes de converter/gerar key/produzir: campo obrigatorio nulo passa pelo
        // builder Avro em silencio e so falharia adiante, fora da classificacao
        validator.validar(payload);

        TipoEventoAutorizacao tipoEvento = derivarTipoEvento(payload);
        EventoAutorizacao evento = paraEventoAvro(payload);
        String key = gerarKey(payload);

        publicador.produzir(key, evento, tipoEvento.name());

        log.info("Autorização produzida com sucesso no tópico eventos-autorizacao: idAutorizacao={} key={} tipoEvento={}",
                payload.idAutorizacao(), key, tipoEvento);
    }

    /**
     * Nunca propaga a exceção do Jackson como {@code cause}: a mensagem de erro de coerção
     * de tipo embute o <em>valor</em> do campo, e {@code id_pessoa_*} (UUID) e
     * {@code valor}/{@code valor_limite} (BigDecimal) são justamente os campos com PII. O
     * {@code getPathReference()} dá o caminho do campo sem o valor — diagnóstico suficiente,
     * sem vazamento no log de descarte (que imprime a stack trace inteira).
     */
    private AutorizacaoEventoPayload desserializar(String mensagemJson) {
        try {
            return objectMapper.readValue(mensagemJson, AutorizacaoEventoPayload.class);
        } catch (JacksonException e) {
            throw new EventoAutorizacaoInvalidoException(
                    "Campo do payload não pôde ser convertido para o tipo esperado: campo="
                            + e.getPathReference() + " causa=" + e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            throw new EventoAutorizacaoInvalidoException(
                    "Body da mensagem não é um JSON válido para o payload de autorização ("
                            + e.getClass().getSimpleName() + ")");
        }
    }

    private TipoEventoAutorizacao derivarTipoEvento(AutorizacaoEventoPayload payload) {
        try {
            return TipoEventoAutorizacao.porStatus(payload.status());
        } catch (RuntimeException e) {
            throw new EventoAutorizacaoInvalidoException(
                    "Status desconhecido no payload: " + payload.status(), e);
        }
    }

    private EventoAutorizacao paraEventoAvro(AutorizacaoEventoPayload payload) {
        try {
            return converter.converter(payload);
        } catch (RuntimeException e) {
            // sem cause: a excecao do Avro pode embutir o valor do campo rejeitado
            throw new EventoAutorizacaoInvalidoException(
                    "Falha ao converter o payload para o schema Avro ("
                            + e.getClass().getSimpleName() + ")");
        }
    }

    private String gerarKey(AutorizacaoEventoPayload payload) {
        try {
            return keyGenerator.gerar(payload.idAutorizacao(), payload.dataHoraUltimaAtualizacao());
        } catch (RuntimeException e) {
            throw new EventoAutorizacaoInvalidoException(
                    "Falha ao gerar a key de idempotência do evento", e);
        }
    }

}
