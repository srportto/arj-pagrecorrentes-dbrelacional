package br.com.srportto.contratoquery.entrypoint.contratosrest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.entities.Autorizacao;
import br.com.srportto.contratoquery.domain.enums.StatusAutorizacao;
import tools.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AutorizacaoResumidaResponseDto {

    private UUID idAutorizacao;
    private LocalDateTime dataCriacao;
    private LocalDate dataInicioVigencia;
    private LocalDate dataFimVigencia;
    private UUID idPessoaRecebedora;
    private String nomeRecebedor;
    private BigDecimal valor;
    private String status;
    private JsonNode metadado;

    public static AutorizacaoResumidaResponseDto from(Autorizacao autorizacao) {
        JsonNode metadadoNode = null;
        if (autorizacao.getMetadados() != null) {
            try {
                metadadoNode = new tools.jackson.databind.ObjectMapper()
                        .readTree(autorizacao.getMetadados());
            } catch (Exception e) {
                metadadoNode = null;
            }
        }

        return AutorizacaoResumidaResponseDto.builder()
                .idAutorizacao(autorizacao.getIdAutorizacao().getIdAutorizacao())
                .dataCriacao(autorizacao.getDataHoraInclusao())
                .dataInicioVigencia(autorizacao.getDataInicioVigencia())
                .dataFimVigencia(autorizacao.getDataFimVigencia())
                .idPessoaRecebedora(autorizacao.getIdPessoaRecebedora())
                .nomeRecebedor(null)
                .valor(autorizacao.getValorAutorizacao())
                .status(mapearStatus(autorizacao.getStatus()))
                .metadado(metadadoNode)
                .build();
    }

    private static String mapearStatus(Integer status) {
        if (status == null) {
            return null;
        }
        return StatusAutorizacao.obterStatusEnumPorIdStatus(status).name();
    }
}
