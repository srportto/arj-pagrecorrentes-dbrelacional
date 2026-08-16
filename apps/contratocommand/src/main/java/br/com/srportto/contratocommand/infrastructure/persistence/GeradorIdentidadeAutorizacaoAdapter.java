package br.com.srportto.contratocommand.infrastructure.persistence;

import br.com.srportto.contratocommand.domain.port.out.GeradorIdentidadeAutorizacao;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Reproduz exatamente o caminho antigo: hash da conta -> partição, UUIDv7 reversível com a partição embutida. */
@Component
public class GeradorIdentidadeAutorizacaoAdapter implements GeradorIdentidadeAutorizacao {

    @Override
    public UUID gerarPara(UUID idUnicoContaContratante) {
        var particao = IdContaUUIDPartitionDistributor.getPartitionFast(idUnicoContaContratante);
        return ReversibleUUIDv7.generate(particao);
    }

}
