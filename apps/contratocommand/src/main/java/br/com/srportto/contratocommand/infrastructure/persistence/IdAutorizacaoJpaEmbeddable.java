package br.com.srportto.contratocommand.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * {@code equals}/{@code hashCode} por valor são exigência do JPA para chave composta
 * ({@code @EmbeddedId}) — não removidos com o resto de {@code @Data} (ver
 * {@code AutorizacaoJpaAdapter}: a instância nunca é mutada depois de detached, é substituída por
 * uma nova, justamente para não invalidar um hashCode já em uso por alguma estrutura).
 */
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
