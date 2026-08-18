package br.com.srportto.contratoquery.infrastructure.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code equals}/{@code hashCode} por valor são exigência do JPA para chave composta ({@code @EmbeddedId}). */
@Getter
@Setter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class IdAutorizacaoJpaEmbeddable {

    @Column(name = "id_autorizacao", nullable = false)
    private UUID idAutorizacao;

    @Column(name = "id_particao_conta", nullable = false)
    private Integer idParticaoConta;
}
