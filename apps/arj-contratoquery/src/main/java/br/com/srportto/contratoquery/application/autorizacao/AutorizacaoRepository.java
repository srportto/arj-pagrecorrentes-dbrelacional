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
     * Nível 2 da cascata de localização: procura na faixa de partições de expurgo, para onde toda
     * autorização em estado terminal é transferida.
     *
     * <p>A busca é por faixa, e não por partição exata, porque a partição de expurgo deriva da
     * <strong>data da transição terminal</strong> — informação que não existe no id. O
     * {@code >=} permite ao PostgreSQL podar as partições quentes no planejamento (medido: 100
     * subplanos em vez de 989).
     *
     * <p>Devolve lista, e não {@code Optional}, para que a presença de mais de uma linha com o
     * mesmo id seja detectável pelo chamador em vez de silenciosamente desempatada.
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
     * Nível 3 da cascata: procura nas demais partições quentes, excluindo a derivada do próprio
     * id (já consultada no nível 1) e a faixa de expurgo (já consultada no nível 2).
     *
     * <p>Encontrar algo aqui significa que a linha violou o invariante "ou está na partição do
     * seu id, ou está no expurgo" — é rede de segurança, não caminho normal. A exclusão explícita
     * do que já foi consultado mantém os três níveis disjuntos, de modo que um acerto neste nível
     * seja, por definição, anomalia.
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
