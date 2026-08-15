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

        // Passo 1: dirty-check do JPA monta UPDATE com AND version=? — é isso que protege contra
        // escrita concorrente de terceiro.
        var autorizacaoAtualizada = repository.saveAndFlush(autorizacao);

        // Passo 2: move via SQL nativo (JPA proíbe alterar @EmbeddedId de entidade gerenciada). A
        // antiga via delete+flush+detach+save quebrava com @Version (StaleObjectStateException).
        int linhasMovidas = repository.moverParaParticao(idAutorizacaoUuid, particaoAntiga, novaParticao);
        if (linhasMovidas != 1) {
            // Só ocorre se a linha sumir entre os passos 1 e 2 (improvável dado o lock do passo 1),
            // mas conferir é barato e evita devolver "transferida" com a linha ainda na origem.
            throw new ObjectOptimisticLockingFailureException(Autorizacao.class, idAutorizacaoUuid,
                    "Movimentação para a partição de expurgo afetou " + linhasMovidas
                            + " linha(s), esperado exatamente 1", null);
        }

        // Passo 3: sincroniza a instância em memória p/ o evento pós-commit e o response DTO;
        // detach precisa vir antes por o JPA não permitir alterar @EmbeddedId gerenciado.
        entityManager.detach(autorizacaoAtualizada);
        autorizacaoAtualizada.getIdAutorizacao().setIdParticaoConta(novaParticao);

        return autorizacaoAtualizada;
    }
}
