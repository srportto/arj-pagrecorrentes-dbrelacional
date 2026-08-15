package br.com.srportto.contratocommand.domain.port.in;

import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Comando de criação de autorização. Livre de DTO de transporte — o driving adapter traduz o request nestes campos. */
public record CriarAutorizacaoCommand(
        TipoJornadaAutorizacao tipoJornada,
        LocalDate dataFimVigencia,
        String tipoProduto,
        BigDecimal valor,
        String idAutorizacaoEmpresa,
        BigDecimal valorLimite,
        Integer frequencia,
        Integer quantidadeDividasCiclo,
        Integer indicadorUsoLimiteConta,
        String codigoCanalContratacao,
        String descricao,
        UUID idUnicoContaContratante,
        UUID idPessoaPagadora,
        UUID idPessoaDevedora,
        UUID idPessoaRecebedora,
        String metadados) {
}
