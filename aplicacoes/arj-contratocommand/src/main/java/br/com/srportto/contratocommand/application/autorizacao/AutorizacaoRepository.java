package br.com.srportto.contratocommand.application.autorizacao;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório único da entidade {@link Autorizacao}, compartilhado por todos os produtos
 * (PIX_AUTO, DDA_AUTO). A variação por produto vive nas strategies e nas regras de negócio,
 * não na persistência — todos os produtos gravam na mesma tabela {@code autorizacoes}.
 */
@Repository
public interface AutorizacaoRepository extends JpaRepository<Autorizacao, IdAutorizacao> {

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
