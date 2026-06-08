package br.com.srportto.contratocommand.entrypoint.contratosrest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import tools.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de resposta resumida para listagem de autorizações.
 * Contém apenas os campos essenciais para exibição em listas paginadas.
 */
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

    /**
     * Converte uma entidade Autorizacao para DTO resumido.
     *
     * @param autorizacao a entidade de autorização
     * @return DTO resumido com campos mínimos
     */
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
                .nomeRecebedor(null) // Placeholder: integração posterior com serviço de pessoas
                .valor(autorizacao.getValorAutorizacao())
                .status(mapearStatus(autorizacao.getStatus()))
                .metadado(metadadoNode)
                .build();
    }

    /**
     * Traduz o código de status persistido para o nome do enum {@link StatusAutorizacao}.
     *
     * @param status código inteiro do status na entidade (pode ser nulo)
     * @return nome do enum (ex.: "ATIVA") ou {@code null} se o status for nulo
     */
    private static String mapearStatus(Integer status) {
        if (status == null) {
            return null;
        }
        return StatusAutorizacao.obterStatusEnumPorIdStatus(status).name();
    }
}
