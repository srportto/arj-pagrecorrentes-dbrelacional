package br.com.srportto.contratocommand.infrastructure.web.contratosrest;

import br.com.srportto.contratocommand.domain.model.Cancelamento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CancelamentoResponseDto {

    private String codigoCanalCancelamento;
    private UUID idPessoaCancelamento;
    private LocalDateTime dataHoraCancelamento;
    private String motivoCancelamento;

    public static CancelamentoResponseDto from(Cancelamento cancelamento) {
        if (cancelamento == null) {
            return null;
        }
        return CancelamentoResponseDto.builder()
                .codigoCanalCancelamento(cancelamento.getCodigoCanalCancelamento())
                .idPessoaCancelamento(cancelamento.getIdPessoaCancelamento())
                .dataHoraCancelamento(cancelamento.getDataHoraCancelamento())
                .motivoCancelamento(cancelamento.getMotivoCancelamento())
                .build();
    }
}
