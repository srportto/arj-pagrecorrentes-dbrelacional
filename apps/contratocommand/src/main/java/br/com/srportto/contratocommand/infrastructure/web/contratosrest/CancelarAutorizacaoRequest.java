package br.com.srportto.contratocommand.infrastructure.web.contratosrest;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Corpo da requisição de cancelamento. Id (path) e produto (header) são parâmetros do fluxo,
 * não campos deste DTO — ver {@code CancelarAutorizacaoCommand}.
 */
public record CancelarAutorizacaoRequest(

        @NotNull(message = "o campo 'codigoCanalCancelamento' é obrigatorio.")
        String codigoCanalCancelamento,

        @NotNull(message = "O campo 'idPessoaCancelamento' é obrigatório.")
        UUID idPessoaCancelamento,

        String motivoCancelamento) {

}
