package br.com.srportto.contratocommand.entrypoint.contratosrest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record CriarAutorizacaoRequest(

    LocalDate dataFimVigencia,

    @NotNull(message = "O campo 'tipoProduto' é obrigatório.")
    String tipoProduto,

    @NotNull(message = "O campo 'valor' é obrigatório.")
    BigDecimal valor,

    @NotNull(message = "O campo 'idAutorizacaoEmpresa' é obrigatório.")
    String idAutorizacaoEmpresa,

    BigDecimal valorLimite,

    @NotNull(message = "O campo 'frequencia' é obrigatório.") 
    @Min(value = 1, message = "O campo 'frequencia' deve ser maior ou igual a 1.") 
    @Max(value = 4, message = "O campo 'frequencia' deve ser menor ou igual a 4.") 
    Integer frequencia,

    @NotNull(message = "O campo 'quantidadeDividasCiclo' é obrigatório.")
    @Min(value = 1, message = "O campo 'quantidadeDividasCiclo' deve ser maior ou igual a 1.")
    Integer quantidadeDividasCiclo,

    @NotNull(message = "O campo 'indicadorUsoLimiteConta' é obrigatório.") 
    Integer indicadorUsoLimiteConta,

    @NotNull(message = "o campo 'codigoCanalContratacao' é obrigatorio.")
    String codigoCanalContratacao,

    String descricao,

    @NotNull(message = "O campo 'idUnicoContaContratante' é obrigatório.") 
    UUID idUnicoContaContratante,

    @NotNull  (message = "O campo 'idPessoaPagadora' é obrigatório.")
    UUID idPessoaPagadora,

    @NotNull(message = "O campo 'idPessoaDevedora' é obrigatório.") 
    UUID idPessoaDevedora,

    @NotNull(message = "O campo 'idPessoaRecebedora' é obrigatório.") 
    UUID idPessoaRecebedora,

    JsonNode metadados) {

}
