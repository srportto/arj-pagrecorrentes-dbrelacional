package br.com.srportto.contratoquery.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class CancelamentoJpaEmbeddable {

    @JoinColumn(name = "codigo_canal_cancelamento", nullable = true)
    private String codigoCanalCancelamento;

    @JoinColumn(name = "id_pessoa_cancelamento", nullable = true, unique = false)
    private UUID idPessoaCancelamento;

    @JoinColumn(name = "data_hora_cancelamento", nullable = true)
    private LocalDateTime dataHoraCancelamento;

    @JoinColumn(name = "motivo_cancelamento", nullable = true)
    private String motivoCancelamento;
}
