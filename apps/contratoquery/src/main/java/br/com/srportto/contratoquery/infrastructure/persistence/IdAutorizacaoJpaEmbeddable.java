package br.com.srportto.contratoquery.infrastructure.persistence;

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
public class IdAutorizacaoJpaEmbeddable {

    @JoinColumn(name = "id_autorizacao", nullable = false)
    private UUID idAutorizacao;

    @JoinColumn(name = "id_particao_conta", nullable = false)
    private Integer idParticaoConta;
}
