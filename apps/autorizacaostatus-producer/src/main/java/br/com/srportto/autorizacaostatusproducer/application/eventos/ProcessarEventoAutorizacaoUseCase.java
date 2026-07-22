package br.com.srportto.autorizacaostatusproducer.application.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Processa uma mensagem consumida da fila de eventos de autorizacao: valida a forma do
 * payload desserializando-o e loga o consumo com sucesso incluindo a representacao da
 * entidade. Nao ha processamento de negocio nesta fase — apenas log + confirmacao (o
 * ack em si e responsabilidade do adapter de consumo, apos este metodo retornar sem
 * lancar excecao).
 */
@Service
public class ProcessarEventoAutorizacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarEventoAutorizacaoUseCase.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void processar(String mensagemJson) {
        AutorizacaoEventoPayload payload = objectMapper.readValue(mensagemJson, AutorizacaoEventoPayload.class);

        log.info("Autorização {} consumida com sucesso: {}", payload.idAutorizacao(), mensagemJson);
    }

}
