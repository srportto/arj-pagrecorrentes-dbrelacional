package br.com.srportto.contratoquery.infrastructure.web.contratosrest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.model.Autorizacao;
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
    private String motivoStatus;
    private JsonNode metadado;

    public static AutorizacaoResumidaResponseDto from(Autorizacao autorizacao) {
        return AutorizacaoResumidaResponseDto.builder()
                .idAutorizacao(autorizacao.getIdAutorizacao())
                .dataCriacao(autorizacao.getDataHoraInclusao())
                .dataInicioVigencia(autorizacao.getDataInicioVigencia())
                .dataFimVigencia(autorizacao.getDataFimVigencia())
                .idPessoaRecebedora(autorizacao.getIdPessoaRecebedora())
                .nomeRecebedor(null)
                .valor(autorizacao.getValorAutorizacao())
                .status(autorizacao.getStatus() == null ? null : autorizacao.getStatus().name())
                .motivoStatus(autorizacao.getMotivoStatus())
                .metadado(MetadadoJsonParser.parse(autorizacao.getMetadados()))
                .build();
    }
}
