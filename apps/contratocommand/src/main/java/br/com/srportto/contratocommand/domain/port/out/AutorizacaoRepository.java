package br.com.srportto.contratocommand.domain.port.out;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Porta de saída de persistência de {@link Autorizacao}, sem dependência de JPA/Spring Data. */
public interface AutorizacaoRepository {

    Autorizacao save(Autorizacao autorizacao);

    /** Busca pela chave composta completa (UUID + partição). */
    Optional<Autorizacao> findByIdAutorizacaoAndParticao(UUID idAutorizacao, Integer idParticaoConta);

    /**
     * Verifica a existência de autorização por chave de negócio (id_autorizacao_empresa), restrita
     * à partição da conta — poda para 1 partição em vez de varrer as ~989 existentes, e casa com o
     * escopo real da constraint UNIQUE (id_particao_conta, id_autorizacao_empresa).
     */
    boolean existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa(
            Integer idParticaoConta, String idAutorizacaoEmpresa);

    /**
     * Transfere a autorização para a partição de expurgo correspondente à data de referência,
     * protegida por lock otimista. Quem calcula a partição de destino e faz o row movement é o
     * adaptador — o domínio pede a transferência, não sabe como ela é feita fisicamente.
     */
    Autorizacao transferirParaExpurgo(Autorizacao autorizacao, LocalDate dataReferenciaExpurgo);

}
