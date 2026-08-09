package br.com.srportto.contratocommand.application;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositório único de {@link Autorizacao}, compartilhado por todos os produtos — a variação por produto vive nas rules, não na persistência. */
public interface AutorizacaoRepository extends JpaRepository<Autorizacao, IdAutorizacao> {

    List<Autorizacao> findByStatus(Integer status);

    /** Busca pela chave composta completa (UUID + partição). */
    @Query("SELECT a FROM Autorizacao a WHERE a.idAutorizacao.idAutorizacao = :idAutorizacao AND a.idAutorizacao.idParticaoConta = :idParticaoConta")
    Optional<Autorizacao> findByIdAutorizacaoAndParticao(
            @Param("idAutorizacao") UUID idAutorizacao,
            @Param("idParticaoConta") Integer idParticaoConta);

    /** Busca por UUID, independentemente da partição. */
    @Query("SELECT a FROM Autorizacao a WHERE a.idAutorizacao.idAutorizacao = :idAutorizacao")
    List<Autorizacao> findByIdAutorizacao(@Param("idAutorizacao") UUID idAutorizacao);

    /**
     * Verifica a existência de autorização por chave de negócio (id_autorizacao_empresa), restrita
     * à partição da conta — poda para 1 partição em vez de varrer as ~989 existentes, e casa com o
     * escopo real da constraint UNIQUE (id_particao_conta, id_autorizacao_empresa).
     */
    boolean existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa(
            Integer idParticaoConta, String idAutorizacaoEmpresa);

}
