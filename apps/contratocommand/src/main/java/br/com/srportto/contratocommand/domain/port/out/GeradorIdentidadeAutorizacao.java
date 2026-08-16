package br.com.srportto.contratocommand.domain.port.out;

import java.util.UUID;

/**
 * Gera a identidade de uma nova autorização a partir da conta contratante. O domínio pede um
 * identificador; quem sabe que ele embute a partição física de armazenamento é o adaptador.
 */
public interface GeradorIdentidadeAutorizacao {

    UUID gerarPara(UUID idUnicoContaContratante);

}
