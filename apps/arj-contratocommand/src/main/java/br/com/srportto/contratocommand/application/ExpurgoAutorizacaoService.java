package br.com.srportto.contratocommand.application;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.utilities.ControleExpurgoAutorizacao;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** Transferência de autorizações em estado terminal para a partição de expurgo, compartilhada por todo use case que leva uma autorização a CANCELADA, REJEITADA, EXPIRADA ou FINALIZADA. */
@Service
@AllArgsConstructor
public class ExpurgoAutorizacaoService {

    private static final Logger log = LoggerFactory.getLogger(ExpurgoAutorizacaoService.class);

    private final AutorizacaoRepository repository;
    private final EntityManager entityManager;

    public Autorizacao transferirParaExpurgo(Autorizacao autorizacao, LocalDate dataReferenciaExpurgo) {
        var novaParticao = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(dataReferenciaExpurgo);
        var particaoAntiga = autorizacao.getIdAutorizacao().getIdParticaoConta();

        if (novaParticao == particaoAntiga.intValue()) {
            return repository.save(autorizacao);
        }

        var idAutorizacaoUuid = autorizacao.getIdAutorizacao().getIdAutorizacao();
        log.info("Transferindo autorização {} da partição {} para partição {}",
                idAutorizacaoUuid, particaoAntiga, novaParticao);

        // 1. Grava as colunas de negócio ainda na partição atual, pelo caminho normal do JPA: o
        // dirty-check monta o UPDATE com `AND version = ?` e incrementa a versão. É este passo — e
        // só ele — que protege contra escrita concorrente de terceiro.
        var autorizacaoAtualizada = repository.saveAndFlush(autorizacao);

        // 2. Move a linha. Não se faz isso pelo JPA: alterar o @EmbeddedId de uma entidade
        // gerenciada é proibido, e a alternativa anterior (delete + flush + detach + save) submetia
        // ao Hibernate uma instância detached cuja linha ele mesmo acabara de apagar, contando com
        // a inferência "id atribuído e sem versão ⇒ provavelmente nova ⇒ INSERT". A introdução de
        // `@Version` transformou esse "não sei" em "tenho certeza de que é detached", e o Hibernate
        // passou a concluir que outra transação apagara a linha — StaleObjectStateException, 409, em
        // toda transferência. Aqui quem move a linha é o próprio PostgreSQL, via row movement.
        int linhasMovidas = repository.moverParaParticao(idAutorizacaoUuid, particaoAntiga, novaParticao);
        if (linhasMovidas != 1) {
            // Só acontece se a linha sumir entre os passos 1 e 2 — o lock de linha tomado no passo
            // 1 torna isso improvável, mas conferir é barato e evita devolver ao chamador uma
            // autorização declarada como transferida enquanto a linha segue na partição de origem.
            throw new ObjectOptimisticLockingFailureException(Autorizacao.class, idAutorizacaoUuid,
                    "Movimentação para a partição de expurgo afetou " + linhasMovidas
                            + " linha(s), esperado exatamente 1", null);
        }

        // 3. Sincroniza a instância em memória com a nova localização física, para o evento
        // publicado após o commit e para o response DTO. O detach vem antes da alteração do
        // @EmbeddedId porque o JPA não permite alterá-lo enquanto a entidade está gerenciada.
        entityManager.detach(autorizacaoAtualizada);
        autorizacaoAtualizada.getIdAutorizacao().setIdParticaoConta(novaParticao);

        return autorizacaoAtualizada;
    }
}
