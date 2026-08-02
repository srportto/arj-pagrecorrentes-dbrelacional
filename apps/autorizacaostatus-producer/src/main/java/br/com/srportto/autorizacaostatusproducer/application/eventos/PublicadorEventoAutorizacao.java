package br.com.srportto.autorizacaostatusproducer.application.eventos;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;

/**
 * Porta de saída da ponte: publica o evento de autorização no destino configurado.
 *
 * <p>Existe para que o use case não dependa da classe concreta do adaptador nem conheça
 * {@code org.apache.kafka.*} — a implementação ({@link KafkaEventoAutorizacaoProducer}) é
 * detalhe de transporte, substituível em teste sem subir contexto Spring.
 */
public interface PublicadorEventoAutorizacao {

    /**
     * Publica o evento de forma síncrona, retornando somente após a confirmação do destino.
     *
     * @param key        chave de idempotência da transição de estado
     * @param evento     evento Avro a publicar
     * @param tipoEvento tipo derivado do status, propagado como metadado da mensagem
     */
    void produzir(String key, EventoAutorizacao evento, String tipoEvento);

}
