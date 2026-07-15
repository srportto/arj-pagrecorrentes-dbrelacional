package br.com.srportto.contratocommand.entrypoint.contratosrest;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Corpo da requisição de cancelamento. Record imutável contendo apenas os dados enviados pelo
 * cliente. O id da autorização (path) e o produto (header) são parâmetros do fluxo, não campos
 * deste DTO — ver {@code CancelamentoContext}.
 */
public record CancelarAutorizacaoRequest(

        @NotNull(message = "o campo 'codigoCanalCancelamento' é obrigatorio.")
        String codigoCanalCancelamento,

        @NotNull(message = "O campo 'idPessoaCancelamento' é obrigatório.")
        UUID idPessoaCancelamento,

        String motivoCancelamento) {

}
