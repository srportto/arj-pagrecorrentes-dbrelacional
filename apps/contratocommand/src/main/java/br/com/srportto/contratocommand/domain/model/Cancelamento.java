package br.com.srportto.contratocommand.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Cancelamento {

    private String codigoCanalCancelamento;
    private UUID idPessoaCancelamento;
    private LocalDateTime dataHoraCancelamento;
    private String motivoCancelamento;

}
