package br.com.srportto.contratocommand.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class Cancelamento {

    @Column(name = "codigo_canal_cancelamento", nullable = true)
    private String codigoCanalCancelamento;

    @Column(name = "id_pessoa_cancelamento", nullable = true, unique = false)
    private UUID idPessoaCancelamento;

    @Column(name = "data_hora_cancelamento", nullable = true)
    private LocalDateTime dataHoraCancelamento;

    @Column(name = "motivo_cancelamento", nullable = true)
    private String motivoCancelamento;

}
