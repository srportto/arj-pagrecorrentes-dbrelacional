package br.com.srportto.contratocommand.application.enabledproduct.pixauto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;

@Repository
public interface PixAutoRepository extends JpaRepository<Autorizacao, IdAutorizacao> {

    List<Autorizacao> findByStatus(Integer status);

    /**
     * Busca uma autorização pela chave composta completa (UUID + partição).
     *
     * @param idAutorizacao   o UUID da autorização
     * @param idParticaoConta o número da partição
     * @return a autorização encontrada, ou vazio se não existir
     */
    @Query("SELECT a FROM Autorizacao a WHERE a.idAutorizacao.idAutorizacao = :idAutorizacao AND a.idAutorizacao.idParticaoConta = :idParticaoConta")
    Optional<Autorizacao> findByIdAutorizacaoAndParticao(
            @Param("idAutorizacao") UUID idAutorizacao,
            @Param("idParticaoConta") Integer idParticaoConta);

     /**
      * Busca todas as autorizações por UUID, independentemente da partição.
      * Útil para cenários onde apenas o UUID é conhecido.
      *
      * @param idAutorizacao o UUID da autorização
      * @return lista de autorizações com o UUID especificado
      */
     @Query("SELECT a FROM Autorizacao a WHERE a.idAutorizacao.idAutorizacao = :idAutorizacao")
     List<Autorizacao> findByIdAutorizacao(@Param("idAutorizacao") UUID idAutorizacao);

}
