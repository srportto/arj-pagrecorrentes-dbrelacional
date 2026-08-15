package br.com.srportto.contratocommand.infrastructure.persistence;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import br.com.srportto.contratocommand.domain.utilities.ControleExpurgoAutorizacao;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Adaptador de persistência de {@link Autorizacao}: implementa a porta de saída sobre Spring Data JPA. */
@Component
@AllArgsConstructor
public class AutorizacaoJpaAdapter implements AutorizacaoRepository {

    private static final Logger log = LoggerFactory.getLogger(AutorizacaoJpaAdapter.class);

    private final SpringDataAutorizacaoRepository springDataRepository;
    private final EntityManager entityManager;

    @Override
    public Autorizacao save(Autorizacao autorizacao) {
        return springDataRepository.save(autorizacao);
    }

    @Override
    public Optional<Autorizacao> findByIdAutorizacaoAndParticao(UUID idAutorizacao, Integer idParticaoConta) {
        return springDataRepository.findByIdAutorizacaoAndParticao(idAutorizacao, idParticaoConta);
    }

    @Override
    public boolean existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa(
            Integer idParticaoConta, String idAutorizacaoEmpresa) {
        return springDataRepository.existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa(
                idParticaoConta, idAutorizacaoEmpresa);
    }

    @Override
    public Autorizacao transferirParaExpurgo(Autorizacao autorizacao, LocalDate dataReferenciaExpurgo) {
        var novaParticao = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(dataReferenciaExpurgo);
        var particaoAntiga = autorizacao.getIdAutorizacao().getIdParticaoConta();

        if (novaParticao == particaoAntiga.intValue()) {
            return springDataRepository.save(autorizacao);
        }

        var idAutorizacaoUuid = autorizacao.getIdAutorizacao().getIdAutorizacao();
        log.info("Transferindo autorização {} da partição {} para partição {}",
                idAutorizacaoUuid, particaoAntiga, novaParticao);

        // Passo 1: dirty-check do JPA monta UPDATE com AND version=? — é isso que protege contra
        // escrita concorrente de terceiro.
        var autorizacaoAtualizada = springDataRepository.saveAndFlush(autorizacao);

        // Passo 2: move via SQL nativo (JPA proíbe alterar @EmbeddedId de entidade gerenciada). A
        // antiga via delete+flush+detach+save quebrava com @Version (StaleObjectStateException).
        int linhasMovidas = springDataRepository.moverParaParticao(idAutorizacaoUuid, particaoAntiga, novaParticao);
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
