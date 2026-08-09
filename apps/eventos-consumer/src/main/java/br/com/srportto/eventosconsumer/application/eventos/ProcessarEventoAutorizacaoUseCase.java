package br.com.srportto.eventosconsumer.application.eventos;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import br.com.srportto.eventosconsumer.domain.enums.TipoEventoAutorizacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Loga o consumo com sucesso do evento, com o tipo derivado do status; o commit do offset é responsabilidade do adapter. */
@Service
public class ProcessarEventoAutorizacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarEventoAutorizacaoUseCase.class);

    public void processar(EventoAutorizacao evento) {
        TipoEventoAutorizacao tipoEvento = TipoEventoAutorizacao.porStatus(evento.getStatus());
        log.info("Autorização {} consumida com sucesso (tipoEvento={})",
                evento.getIdAutorizacao(), tipoEvento);
    }

}
