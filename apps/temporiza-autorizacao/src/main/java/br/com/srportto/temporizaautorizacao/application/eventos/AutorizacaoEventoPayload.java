package br.com.srportto.temporizaautorizacao.application.eventos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Subconjunto do payload publicado pelo arj-contratocommand (espelho parcial de
 * {@code AutorizacaoEventoPayload} lá) — esta aplicação só precisa do id e do instante de
 * inclusão para calcular o vencimento; os demais campos do evento são ignorados
 * (a filter policy da subscription já garante que só chegam eventos de recepção de
 * PIX_AUTO em SPI_J1).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AutorizacaoEventoPayload(

        @JsonProperty("id_autorizacao") UUID idAutorizacao,

        @JsonProperty("data_hora_inclusao") LocalDateTime dataHoraInclusao) {
}
