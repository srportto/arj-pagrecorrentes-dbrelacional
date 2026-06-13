package br.com.srportto.contratoquery.entrypoint.contratosrest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.entities.Autorizacao;
import br.com.srportto.contratoquery.domain.enums.StatusAutorizacao;
import br.com.srportto.contratoquery.domain.enums.TipoProduto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representacao completa de uma autorizacao, usada na consulta por id
 * (GET /api/autorizacoes/{autorizacaoId}).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AutorizacaoDetalheResponseDto {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private UUID idAutorizacao;
    private TipoProduto tipoProduto;
    private String status;
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
                .idAutorizacao(autorizacao.getIdAutorizacao().getIdAutorizacao())
                .tipoProduto(autorizacao.getTipoProduto())
                .status(mapearStatus(autorizacao.getStatus()))
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
                .metadado(parsearMetadado(autorizacao.getMetadados()))
                .build();
    }

    private static String mapearStatus(Integer status) {
        if (status == null) {
            return null;
        }
        return StatusAutorizacao.obterStatusEnumPorIdStatus(status).name();
    }

    private static JsonNode parsearMetadado(String metadados) {
        if (metadados == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(metadados);
        } catch (Exception e) {
            return null;
        }
    }
}
