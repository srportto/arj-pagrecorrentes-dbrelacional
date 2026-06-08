package br.com.srportto.contratocommand.entrypoint.contratosrest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.Cancelamento;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AutorizacaoCompletaResponseDto {

    private UUID idAutorizacao;
    private LocalDate dataFimVigencia;
    private TipoProduto tipoProduto;
    private Integer status;
    private String motivoStatus;
    private LocalDate dataInicioVigencia;
    private LocalDateTime dataHoraInclusao;
    private LocalDateTime dataHoraUltimaAtualizacao;
    private BigDecimal valorAutorizacao;
    private String idAutorizacaoEmpresa;
    private BigDecimal valorLimite;
    private Short frequenciaPagamento;
    private Short quantidadeDividasCiclo;
    private Short indicadorUsoLimiteConta;
    private Short indicadorTipoMensageria;
    private String codigoCanalContratacao;
    private String descricao;
    private UUID idUnicoContaContratante;
    private UUID idPessoaPagadora;
    private UUID idPessoaDevedora;
    private UUID idPessoaRecebedora;
    private Cancelamento cancelamento;
    private JsonNode metadados;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static AutorizacaoCompletaResponseDto from(Autorizacao autorizacao) {
        return AutorizacaoCompletaResponseDto.builder()
                .idAutorizacao(autorizacao.getIdAutorizacao().getIdAutorizacao())
                .dataFimVigencia(autorizacao.getDataFimVigencia())
                .tipoProduto(autorizacao.getTipoProduto())
                .status(autorizacao.getStatus())
                .motivoStatus(autorizacao.getMotivoStatus())
                .dataInicioVigencia(autorizacao.getDataInicioVigencia())
                .dataHoraInclusao(autorizacao.getDataHoraInclusao())
                .dataHoraUltimaAtualizacao(autorizacao.getDataHoraUltimaAtualizacao())
                .valorAutorizacao(autorizacao.getValorAutorizacao())
                .idAutorizacaoEmpresa(autorizacao.getIdAutorizacaoEmpresa())
                .valorLimite(autorizacao.getValorLimite())
                .frequenciaPagamento(autorizacao.getFrequenciaPagamento())
                .quantidadeDividasCiclo(autorizacao.getQuantidadeDividasCiclo())
                .indicadorUsoLimiteConta(autorizacao.getIndicadorUsoLimiteConta())
                .indicadorTipoMensageria(autorizacao.getIndicadorTipoMensageria())
                .codigoCanalContratacao(autorizacao.getCodigoCanalContratacao())
                .descricao(autorizacao.getDescricao())
                .idUnicoContaContratante(autorizacao.getIdUnicoContaContratante())
                .idPessoaPagadora(autorizacao.getIdPessoaPagadora())
                .idPessoaDevedora(autorizacao.getIdPessoaDevedora())
                .idPessoaRecebedora(autorizacao.getIdPessoaRecebedora())
                .cancelamento(autorizacao.getCancelamento())
                .metadados(parseMetadados(autorizacao.getMetadados()))
                .build();
    }

    private static JsonNode parseMetadados(String metadados) {
        if (metadados == null || metadados.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }

        try {
            return MAPPER.readTree(metadados);
        } catch (Exception e) {
            return JsonNodeFactory.instance.objectNode();
        }
    }
}
