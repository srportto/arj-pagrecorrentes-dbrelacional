package br.com.srportto.contratoquery.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repositório Spring Data — package-private (D5): só {@link AutorizacaoJpaAdapter}, no mesmo
 * pacote, o enxerga. É o que impede injeção acidental em {@code application}.
 */
interface SpringDataAutorizacaoRepository extends JpaRepository<AutorizacaoJpaEntity, IdAutorizacaoJpaEmbeddable> {

    // Varre as 889 partições quentes (idUnicoContaContratante não é a chave de particionamento).
    // plan_cache_mode=force_generic_plan amortiza o planejamento, não a execução — ver design.md
    // de reduzir-custo-planejamento-consultas.
    @Query("SELECT a FROM AutorizacaoJpaEntity a WHERE a.idUnicoContaContratante = :idUnicoContaContratante AND a.status IN :statuses")
    Page<AutorizacaoJpaEntity> findByIdUnicoContaContratanteAndStatusIn(
            @Param("idUnicoContaContratante") UUID idUnicoContaContratante,
            @Param("statuses") List<Integer> statuses,
            Pageable pageable);

    @Query("SELECT a FROM AutorizacaoJpaEntity a WHERE a.idUnicoContaContratante = :idUnicoContaContratante")
    Page<AutorizacaoJpaEntity> findByIdUnicoContaContratante(
            @Param("idUnicoContaContratante") UUID idUnicoContaContratante,
            Pageable pageable);

    /**
     * Nível 2 da cascata: faixa de expurgo, para onde toda autorização em estado terminal é
     * transferida. Busca por faixa (não partição exata) porque a partição de expurgo deriva da
     * data da transição, que não existe no id; {@code >=} permite ao PostgreSQL podar as
     * partições quentes. Devolve lista, não {@code Optional}, para o chamador detectar duplicidade.
     */
    @Query("""
            SELECT a FROM AutorizacaoJpaEntity a
             WHERE a.idAutorizacao.idAutorizacao = :idAutorizacao
               AND a.idAutorizacao.idParticaoConta >= :primeiraParticaoExpurgo
            """)
    List<AutorizacaoJpaEntity> buscarNaFaixaDeExpurgo(
            @Param("idAutorizacao") UUID idAutorizacao,
            @Param("primeiraParticaoExpurgo") int primeiraParticaoExpurgo);

    /**
     * Nível 3 da cascata: demais partições quentes, excluindo as já cobertas pelos níveis 1 e 2.
     * Achar algo aqui é anomalia — viola o invariante "ou está na partição do id, ou no expurgo".
     */
    @Query("""
            SELECT a FROM AutorizacaoJpaEntity a
             WHERE a.idAutorizacao.idAutorizacao = :idAutorizacao
               AND a.idAutorizacao.idParticaoConta < :primeiraParticaoExpurgo
               AND a.idAutorizacao.idParticaoConta <> :particaoJaConsultada
            """)
    List<AutorizacaoJpaEntity> buscarEmOutrasParticoesQuentes(
            @Param("idAutorizacao") UUID idAutorizacao,
            @Param("primeiraParticaoExpurgo") int primeiraParticaoExpurgo,
            @Param("particaoJaConsultada") int particaoJaConsultada);
}
