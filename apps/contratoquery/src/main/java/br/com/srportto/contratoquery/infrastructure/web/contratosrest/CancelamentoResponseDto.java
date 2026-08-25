package br.com.srportto.contratoquery.infrastructure.web.contratosrest;

import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.model.Cancelamento;

/** Espelha {@code CancelamentoResponseDto} do contratocommand, como record (convenção local). */
public record CancelamentoResponseDto(
        String codigoCanalCancelamento,
        UUID idPessoaCancelamento,
        LocalDateTime dataHoraCancelamento,
        String motivoCancelamento) {

    public static CancelamentoResponseDto from(Cancelamento cancelamento) {
        if (cancelamento == null) {
            return null;
        }
        return new CancelamentoResponseDto(
                cancelamento.getCodigoCanalCancelamento(),
                cancelamento.getIdPessoaCancelamento(),
                cancelamento.getDataHoraCancelamento(),
                cancelamento.getMotivoCancelamento());
    }
}
