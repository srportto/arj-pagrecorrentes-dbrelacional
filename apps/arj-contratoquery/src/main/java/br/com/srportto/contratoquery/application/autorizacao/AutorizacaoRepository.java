package br.com.srportto.contratoquery.application.autorizacao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.entities.Autorizacao;
import br.com.srportto.contratoquery.domain.entities.IdAutorizacao;

public interface AutorizacaoRepository extends JpaRepository<Autorizacao, IdAutorizacao> {

    // Varre as 889 partições quentes (idUnicoContaContratante não é a chave de particionamento).
    // plan_cache_mode=force_generic_plan amortiza o planejamento, não a execução — ver design.md
    // de reduzir-custo-planejamento-consultas.
    @Query("SELECT a FROM Autorizacao a WHERE a.idUnicoContaContratante = :idUnicoContaContratante AND a.status IN :statuses")
    Page<Autorizacao> findByIdUnicoContaContratanteAndStatusIn(
            @Param("idUnicoContaContratante") UUID idUnicoContaContratante,
            @Param("statuses") List<Integer> statuses,
            Pageable pageable);

    @Query("SELECT a FROM Autorizacao a WHERE a.idUnicoContaContratante = :idUnicoContaContratante")
    Page<Autorizacao> findByIdUnicoContaContratante(
            @Param("idUnicoContaContratante") UUID idUnicoContaContratante,
            Pageable pageable);

    /**
     * Nível 2 da cascata: faixa de expurgo, para onde toda autorização em estado terminal é
     * transferida. Busca por faixa (não partição exata) porque a partição de expurgo deriva da
     * data da transição, que não existe no id; {@code >=} permite ao PostgreSQL podar as
     * partições quentes. Devolve lista, não {@code Optional}, para o chamador detectar duplicidade.
     */
    @Query("""
            SELECT a FROM Autorizacao a
             WHERE a.idAutorizacao.idAutorizacao = :idAutorizacao
               AND a.idAutorizacao.idParticaoConta >= :primeiraParticaoExpurgo
            """)
    List<Autorizacao> buscarNaFaixaDeExpurgo(
            @Param("idAutorizacao") UUID idAutorizacao,
            @Param("primeiraParticaoExpurgo") int primeiraParticaoExpurgo);

    /**
     * Nível 3 da cascata: demais partições quentes, excluindo as já cobertas pelos níveis 1 e 2.
     * Achar algo aqui é anomalia — viola o invariante "ou está na partição do id, ou no expurgo".
     */
    @Query("""
            SELECT a FROM Autorizacao a
             WHERE a.idAutorizacao.idAutorizacao = :idAutorizacao
               AND a.idAutorizacao.idParticaoConta < :primeiraParticaoExpurgo
               AND a.idAutorizacao.idParticaoConta <> :particaoJaConsultada
            """)
    List<Autorizacao> buscarEmOutrasParticoesQuentes(
            @Param("idAutorizacao") UUID idAutorizacao,
            @Param("primeiraParticaoExpurgo") int primeiraParticaoExpurgo,
            @Param("particaoJaConsultada") int particaoJaConsultada);
}
