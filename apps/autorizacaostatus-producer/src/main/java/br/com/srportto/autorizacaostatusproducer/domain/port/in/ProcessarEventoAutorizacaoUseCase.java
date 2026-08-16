package br.com.srportto.autorizacaostatusproducer.domain.port.in;

import br.com.srportto.autorizacaostatusproducer.domain.enums.TipoEventoAutorizacao;
import br.com.srportto.autorizacaostatusproducer.domain.model.EventoAutorizacao;

/**
 * Porta de entrada: recebe o evento já desserializado, validado e convertido pelo driving
 * adapter, e orquestra chave de idempotência + publicação (ver design.md D1).
 */
public interface ProcessarEventoAutorizacaoUseCase {

    void processar(EventoAutorizacao evento, TipoEventoAutorizacao tipoEvento);

}
