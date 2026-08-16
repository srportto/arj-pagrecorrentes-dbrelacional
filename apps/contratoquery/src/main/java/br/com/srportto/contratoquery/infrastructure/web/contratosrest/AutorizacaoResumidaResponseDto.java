package br.com.srportto.contratoquery.infrastructure.web.contratosrest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.model.Autorizacao;
import br.com.srportto.contratoquery.domain.enums.StatusAutorizacao;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AutorizacaoResumidaResponseDto {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private UUID idAutorizacao;
    private LocalDateTime dataCriacao;
    private LocalDate dataInicioVigencia;
    private LocalDate dataFimVigencia;
    private UUID idPessoaRecebedora;
    private String nomeRecebedor;
    private BigDecimal valor;
    private String status;
    private String motivoStatus;
    private JsonNode metadado;

    public static AutorizacaoResumidaResponseDto from(Autorizacao autorizacao) {
        JsonNode metadadoNode = null;
        if (autorizacao.getMetadados() != null) {
            try {
                metadadoNode = OBJECT_MAPPER.readTree(autorizacao.getMetadados());
            } catch (Exception e) {
                metadadoNode = null;
            }
        }

        return AutorizacaoResumidaResponseDto.builder()
                .idAutorizacao(autorizacao.getIdAutorizacao())
                .dataCriacao(autorizacao.getDataHoraInclusao())
                .dataInicioVigencia(autorizacao.getDataInicioVigencia())
                .dataFimVigencia(autorizacao.getDataFimVigencia())
                .idPessoaRecebedora(autorizacao.getIdPessoaRecebedora())
                .nomeRecebedor(null)
                .valor(autorizacao.getValorAutorizacao())
                .status(mapearStatus(autorizacao.getStatus()))
                .motivoStatus(autorizacao.getMotivoStatus())
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
