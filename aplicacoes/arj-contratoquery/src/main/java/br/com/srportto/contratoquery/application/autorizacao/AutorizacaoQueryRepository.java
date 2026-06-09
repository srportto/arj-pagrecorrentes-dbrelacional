package br.com.srportto.contratoquery.application.autorizacao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.entities.Autorizacao;
import br.com.srportto.contratoquery.domain.entities.IdAutorizacao;

@Repository
public interface AutorizacaoQueryRepository extends JpaRepository<Autorizacao, IdAutorizacao> {

    @Query("SELECT a FROM Autorizacao a WHERE a.idUnicoContaContratante = :idUnicoContaContratante AND a.status IN :statuses")
    Page<Autorizacao> findByIdUnicoContaContratanteAndStatusIn(
            @Param("idUnicoContaContratante") UUID idUnicoContaContratante,
            @Param("statuses") List<Integer> statuses,
            Pageable pageable);

    @Query("SELECT a FROM Autorizacao a WHERE a.idUnicoContaContratante = :idUnicoContaContratante")
    Page<Autorizacao> findByIdUnicoContaContratante(
            @Param("idUnicoContaContratante") UUID idUnicoContaContratante,
            Pageable pageable);
}
