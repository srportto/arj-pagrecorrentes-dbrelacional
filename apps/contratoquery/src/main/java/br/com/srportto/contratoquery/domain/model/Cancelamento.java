package br.com.srportto.contratoquery.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

/** Java puro, imutável — espelha {@code infrastructure/persistence/CancelamentoJpaEmbeddable}. */
@Value
@Builder
public class Cancelamento {

    String codigoCanalCancelamento;
    UUID idPessoaCancelamento;
    LocalDateTime dataHoraCancelamento;
    String motivoCancelamento;
}
