package br.com.srportto.eventosconsumer.application.eventos;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Loga o consumo com sucesso do evento de autorizacao. Nao ha processamento de negocio
 * nesta fase — apenas log; o commit do offset (ack) e responsabilidade do adapter de
 * consumo, apos este metodo retornar sem lancar excecao.
 */
@Service
public class ProcessarEventoAutorizacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarEventoAutorizacaoUseCase.class);

    public void processar(EventoAutorizacao evento, String tipoEvento) {
        log.info("Autorização {} consumida com sucesso (tipoEvento={}): {}",
                evento.getIdAutorizacao(), tipoEvento, evento);
    }

}
