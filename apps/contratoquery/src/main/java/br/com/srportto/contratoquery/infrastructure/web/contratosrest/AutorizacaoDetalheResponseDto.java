package br.com.srportto.contratoquery.infrastructure.web.contratosrest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.enums.TipoProduto;
import br.com.srportto.contratoquery.domain.model.Autorizacao;
import tools.jackson.databind.JsonNode;

/** Representação completa de uma autorização, usada em GET /api/autorizacoes/{autorizacaoId}. */
public record AutorizacaoDetalheResponseDto(
        UUID idAutorizacao,
        TipoProduto tipoProduto,
        String status,
        String motivoStatus,
        LocalDate dataInicioVigencia,
        LocalDate dataFimVigencia,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao,
        BigDecimal valor,
        BigDecimal valorLimite,
        UUID idUnicoContaContratante,
        UUID idPessoaPagadora,
        UUID idPessoaDevedora,
        UUID idPessoaRecebedora,
        String idAutorizacaoEmpresa,
        String descricao,
        JsonNode metadado) {

    public static AutorizacaoDetalheResponseDto from(Autorizacao autorizacao) {
        return new AutorizacaoDetalheResponseDto(
                autorizacao.getIdAutorizacao(),
                autorizacao.getTipoProduto(),
                autorizacao.getStatus() == null ? null : autorizacao.getStatus().name(),
                autorizacao.getMotivoStatus(),
                autorizacao.getDataInicioVigencia(),
                autorizacao.getDataFimVigencia(),
                autorizacao.getDataHoraInclusao(),
                autorizacao.getDataHoraUltimaAtualizacao(),
                autorizacao.getValorAutorizacao(),
                autorizacao.getValorLimite(),
                autorizacao.getIdUnicoContaContratante(),
                autorizacao.getIdPessoaPagadora(),
                autorizacao.getIdPessoaDevedora(),
                autorizacao.getIdPessoaRecebedora(),
                autorizacao.getIdAutorizacaoEmpresa(),
                autorizacao.getDescricao(),
                MetadadoJsonParser.parse(autorizacao.getMetadados()));
    }
}
