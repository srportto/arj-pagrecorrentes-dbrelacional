package br.com.srportto.temporizaautorizacao.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Subconjunto do payload do contratocommand — só id e instante de inclusão, necessários ao
 * vencimento. Demais campos ignorados (filter policy da subscription já filtra o evento).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AutorizacaoEventoPayload(

        @JsonProperty("id_autorizacao") UUID idAutorizacao,

        @JsonProperty("data_hora_inclusao") LocalDateTime dataHoraInclusao) {
}
