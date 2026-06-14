package br.com.srportto.contratocommand.domain.entities;

import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import lombok.*;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Embeddable
public class IdAutorizacao {

    @JoinColumn(name = "id_autorizacao", nullable = false)
    private UUID idAutorizacao;

    @JoinColumn(name = "id_particao_conta", nullable = false)
    private Integer idParticaoConta;

}
