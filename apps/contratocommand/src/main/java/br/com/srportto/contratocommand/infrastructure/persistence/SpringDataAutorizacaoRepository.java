package br.com.srportto.contratocommand.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositório único de {@link AutorizacaoJpaEntity}, compartilhado por todos os produtos — a variação por produto vive nas rules, não na persistência. Package-private: só {@link AutorizacaoJpaAdapter} conhece Spring Data. */
interface SpringDataAutorizacaoRepository extends JpaRepository<AutorizacaoJpaEntity, IdAutorizacaoJpaEmbeddable> {

    List<AutorizacaoJpaEntity> findByStatus(Integer status);

    /** Busca pela chave composta completa (UUID + partição). */
    @Query("SELECT a FROM AutorizacaoJpaEntity a WHERE a.idAutorizacao.idAutorizacao = :idAutorizacao AND a.idAutorizacao.idParticaoConta = :idParticaoConta")
    Optional<AutorizacaoJpaEntity> findByIdAutorizacaoAndParticao(
            @Param("idAutorizacao") UUID idAutorizacao,
            @Param("idParticaoConta") Integer idParticaoConta);

    /** Busca por UUID, independentemente da partição. */
    @Query("SELECT a FROM AutorizacaoJpaEntity a WHERE a.idAutorizacao.idAutorizacao = :idAutorizacao")
    List<AutorizacaoJpaEntity> findByIdAutorizacao(@Param("idAutorizacao") UUID idAutorizacao);

    /**
     * Verifica a existência de autorização por chave de negócio (id_autorizacao_empresa), restrita
     * à partição da conta — poda para 1 partição em vez de varrer as ~989 existentes, e casa com o
     * escopo real da constraint UNIQUE (id_particao_conta, id_autorizacao_empresa).
     */
    boolean existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa(
            Integer idParticaoConta, String idAutorizacaoEmpresa);

    /**
     * SQL nativo por necessidade: JPA não altera a PK de entidade gerenciada. O PostgreSQL (≥ 11)
     * faz o row movement entre partições sozinho e atomicamente. Ver {@code
     * corrigir-expurgo-merge-version} (a alternativa via delete+flush+detach+save quebrava com
     * {@code @Version}).
     *
     * @return quantidade de linhas afetadas; o chamador SHALL tratar valor diferente de 1
     */
    @Modifying
    @Query(value = """
            UPDATE autorizacoes
               SET id_particao_conta = :novaParticao
             WHERE id_autorizacao = :idAutorizacao
               AND id_particao_conta = :particaoAtual
            """, nativeQuery = true)
    int moverParaParticao(@Param("idAutorizacao") UUID idAutorizacao,
            @Param("particaoAtual") Integer particaoAtual,
            @Param("novaParticao") Integer novaParticao);

}
