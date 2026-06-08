package br.com.srportto.contratocommand.application.enabledproduct.ddaauto;

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
public interface DdaAutoRepository extends JpaRepository<Autorizacao, IdAutorizacao> {

    List<Autorizacao> findByStatus(Integer status);

    /**
     * Busca uma autorização DDA pela chave composta completa (UUID + partição).
     *
     * @param idAutorizacao   o UUID da autorização
     * @param idParticaoConta o número da partição
     * @return a autorização encontrada, ou vazio se não existir
     */
    @Query("SELECT a FROM Autorizacao a WHERE a.idAutorizacao.idAutorizacao = :idAutorizacao AND a.idAutorizacao.idParticaoConta = :idParticaoConta")
    Optional<Autorizacao> findByIdAutorizacaoAndParticao(
            @Param("idAutorizacao") UUID idAutorizacao,
            @Param("idParticaoConta") Integer idParticaoConta);

}