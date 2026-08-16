package br.com.srportto.autorizacaostatusproducer.application.usecase;

import br.com.srportto.autorizacaostatusproducer.domain.enums.TipoEventoAutorizacao;
import br.com.srportto.autorizacaostatusproducer.domain.model.EventoAutorizacao;
import br.com.srportto.autorizacaostatusproducer.domain.port.in.ProcessarEventoAutorizacaoUseCase;
import br.com.srportto.autorizacaostatusproducer.domain.port.out.PublicadorEventoAutorizacao;
import br.com.srportto.autorizacaostatusproducer.domain.service.IdempotenciaKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orquestra a publicação do evento já convertido (ponte SQS -> Kafka): deriva a chave de
 * idempotência e publica pela porta.
 *
 * <p>Nenhum log nem mensagem de exceção daqui carrega o body do payload: ele contém dado
 * pessoal (id_pessoa_pagadora/devedora/recebedora, valor, descricao, metadados). A mensagem
 * é identificada por idAutorizacao, key e tipoEvento.
 */
@Service
public class ProcessarEventoAutorizacaoService implements ProcessarEventoAutorizacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarEventoAutorizacaoService.class);

    private final IdempotenciaKeyGenerator keyGenerator;
    private final PublicadorEventoAutorizacao publicador;

    public ProcessarEventoAutorizacaoService(IdempotenciaKeyGenerator keyGenerator,
            PublicadorEventoAutorizacao publicador) {
        this.keyGenerator = keyGenerator;
        this.publicador = publicador;
    }

    @Override
    public void processar(EventoAutorizacao evento, TipoEventoAutorizacao tipoEvento) {
        String key = keyGenerator.gerar(evento.idAutorizacao(), evento.dataHoraUltimaAtlz());

        publicador.produzir(key, evento, tipoEvento.name());

        log.info("Autorização produzida com sucesso no tópico eventos-autorizacao: idAutorizacao={} key={} tipoEvento={}",
                evento.idAutorizacao(), key, tipoEvento);
    }

}
