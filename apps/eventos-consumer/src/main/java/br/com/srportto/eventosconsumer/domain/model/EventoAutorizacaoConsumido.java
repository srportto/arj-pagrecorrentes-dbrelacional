package br.com.srportto.eventosconsumer.domain.model;

import java.util.UUID;

/**
 * Modelo de domínio puro do evento consumido — sem nenhum import de {@code org.apache.avro.*}.
 * Só carrega o que o domínio desta app realmente usa (deriva o tipo de evento e loga); o record
 * Avro gerado pelo plugin fica confinado à infraestrutura de mensageria, que traduz Avro→domínio
 * antes de chamar o caso de uso.
 */
public record EventoAutorizacaoConsumido(UUID idAutorizacao, int status) {
}
