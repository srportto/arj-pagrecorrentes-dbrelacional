package br.com.srportto.autorizacaostatusproducer.domain.port.out;

import br.com.srportto.autorizacaostatusproducer.domain.model.EventoAutorizacao;

/**
 * Porta de saída da ponte: publica o evento de autorização no destino configurado.
 *
 * <p>Existe para que o use case não dependa da classe concreta do adaptador nem conheça
 * {@code org.apache.kafka.*} — a implementação
 * ({@link br.com.srportto.autorizacaostatusproducer.infrastructure.messaging.KafkaEventoAutorizacaoProducer})
 * é detalhe de transporte, substituível em teste sem subir contexto Spring.
 *
 * <p>Usa o tipo puro {@code domain/model/EventoAutorizacao} (D2-b de design.md), não o record
 * Avro gerado — o mapeamento para Avro fica dentro do adaptador de saída, que é quem
 * efetivamente conhece o formato de fio.
 */
public interface PublicadorEventoAutorizacao {

    /**
     * Publica o evento de forma síncrona, retornando somente após a confirmação do destino.
     *
     * @param key        chave de idempotência da transição de estado
     * @param evento     evento de domínio a publicar
     * @param tipoEvento tipo derivado do status, propagado como metadado da mensagem
     */
    void produzir(String key, EventoAutorizacao evento, String tipoEvento);

}
