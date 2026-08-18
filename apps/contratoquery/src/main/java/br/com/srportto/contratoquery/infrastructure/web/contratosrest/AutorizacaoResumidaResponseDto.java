package br.com.srportto.contratoquery.infrastructure.web.contratosrest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.model.Autorizacao;
import tools.jackson.databind.JsonNode;

public record AutorizacaoResumidaResponseDto(
        UUID idAutorizacao,
        LocalDateTime dataCriacao,
        LocalDate dataInicioVigencia,
        LocalDate dataFimVigencia,
        UUID idPessoaRecebedora,
        String nomeRecebedor,
        BigDecimal valor,
        String status,
        String motivoStatus,
        JsonNode metadado) {

    public static AutorizacaoResumidaResponseDto from(Autorizacao autorizacao) {
        return new AutorizacaoResumidaResponseDto(
                autorizacao.getIdAutorizacao(),
                autorizacao.getDataHoraInclusao(),
                autorizacao.getDataInicioVigencia(),
                autorizacao.getDataFimVigencia(),
                autorizacao.getIdPessoaRecebedora(),
                null,
                autorizacao.getValorAutorizacao(),
                autorizacao.getStatus() == null ? null : autorizacao.getStatus().name(),
                autorizacao.getMotivoStatus(),
                MetadadoJsonParser.parse(autorizacao.getMetadados()));
    }
}
