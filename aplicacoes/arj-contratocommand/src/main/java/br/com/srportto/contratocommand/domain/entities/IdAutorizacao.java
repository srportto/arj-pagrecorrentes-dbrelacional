package br.com.srportto.contratocommand.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Embeddable
public class IdAutorizacao {

    @Column(name = "id_autorizacao", nullable = false)
    private UUID idAutorizacao;

    @Column(name = "id_particao_conta", nullable = false)
    private Integer idParticaoConta;

}
