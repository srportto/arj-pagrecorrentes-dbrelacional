package br.com.srportto.contratocommand.infrastructure.web.contratosrest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Corpo da atualização parcial de dados da recorrência. Id (path) e produto (header) são
 * parâmetros do fluxo, não campos deste DTO — ver {@code AtualizarDadosRecorrenciaCommand}.
 * Campo ausente ou {@code null} entre os 4 campos de dado significa "não altera" (ver design.md,
 * D3) — por isso nenhum deles tem {@code @NotNull}.
 */
public record AtualizarDadosRecorrenciaRequest(

        BigDecimal valorLimite,

        LocalDate dataFimVigencia,

        Integer indicadorUsoLimiteConta,

        @Min(value = 1, message = "O campo 'quantidadeDividasCiclo' deve ser maior ou igual a 1.")
        Integer quantidadeDividasCiclo,

        @NotNull(message = "O campo 'codigoCanalAtualizacao' é obrigatório.")
        String codigoCanalAtualizacao,

        @NotNull(message = "O campo 'idPessoaAtualizacao' é obrigatório.")
        UUID idPessoaAtualizacao) {

}
