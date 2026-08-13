package br.com.srportto.contratocommand.entrypoint.contratosrest;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Corpo da decisão sobre autorização em RECEBIDA. {@code acao} é validado contra o enum
 * {@code AcaoDecisao} pela rule {@code AcaoDecisaoValida} (BusinessException, não Enum.valueOf).
 */
public record DecisaoAutorizacaoRequest(

        @NotNull(message = "O campo 'acao' é obrigatório.")
        String acao,

        String codigoCanalDecisao,

        UUID idPessoaDecisao) {

}
