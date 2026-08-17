package br.com.srportto.contratoquery.infrastructure.web.contratosrest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.model.Autorizacao;
import br.com.srportto.contratoquery.domain.enums.TipoProduto;
import tools.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Representação completa de uma autorização, usada em GET /api/autorizacoes/{autorizacaoId}. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AutorizacaoDetalheResponseDto {

    private UUID idAutorizacao;
    private TipoProduto tipoProduto;
    private String status;
    private String motivoStatus;
    private LocalDate dataInicioVigencia;
    private LocalDate dataFimVigencia;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;
    private BigDecimal valor;
    private BigDecimal valorLimite;
    private UUID idUnicoContaContratante;
    private UUID idPessoaPagadora;
    private UUID idPessoaDevedora;
    private UUID idPessoaRecebedora;
    private String idAutorizacaoEmpresa;
    private String descricao;
    private JsonNode metadado;

    public static AutorizacaoDetalheResponseDto from(Autorizacao autorizacao) {
        return AutorizacaoDetalheResponseDto.builder()
                .idAutorizacao(autorizacao.getIdAutorizacao())
                .tipoProduto(autorizacao.getTipoProduto())
                .status(autorizacao.getStatus() == null ? null : autorizacao.getStatus().name())
                .motivoStatus(autorizacao.getMotivoStatus())
                .dataInicioVigencia(autorizacao.getDataInicioVigencia())
                .dataFimVigencia(autorizacao.getDataFimVigencia())
                .dataCriacao(autorizacao.getDataHoraInclusao())
                .dataAtualizacao(autorizacao.getDataHoraUltimaAtualizacao())
                .valor(autorizacao.getValorAutorizacao())
                .valorLimite(autorizacao.getValorLimite())
                .idUnicoContaContratante(autorizacao.getIdUnicoContaContratante())
                .idPessoaPagadora(autorizacao.getIdPessoaPagadora())
                .idPessoaDevedora(autorizacao.getIdPessoaDevedora())
                .idPessoaRecebedora(autorizacao.getIdPessoaRecebedora())
                .idAutorizacaoEmpresa(autorizacao.getIdAutorizacaoEmpresa())
                .descricao(autorizacao.getDescricao())
                .metadado(MetadadoJsonParser.parse(autorizacao.getMetadados()))
                .build();
    }
}
