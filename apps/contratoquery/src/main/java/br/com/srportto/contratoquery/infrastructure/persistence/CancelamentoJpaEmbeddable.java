package br.com.srportto.contratoquery.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class CancelamentoJpaEmbeddable {

    @Column(name = "codigo_canal_cancelamento", nullable = true)
    private String codigoCanalCancelamento;

    @Column(name = "id_pessoa_cancelamento", nullable = true, unique = false)
    private UUID idPessoaCancelamento;

    @Column(name = "data_hora_cancelamento", nullable = true)
    private LocalDateTime dataHoraCancelamento;

    @Column(name = "motivo_cancelamento", nullable = true)
    private String motivoCancelamento;
}
