package br.com.srportto.contratocommand.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class IdAutorizacaoJpaEmbeddable {

    @Column(name = "id_autorizacao", nullable = false)
    private UUID idAutorizacao;

    @Column(name = "id_particao_conta", nullable = false)
    private Integer idParticaoConta;

}
