package br.com.srportto.contratocommand.domain.entities;

import java.util.UUID;

import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
