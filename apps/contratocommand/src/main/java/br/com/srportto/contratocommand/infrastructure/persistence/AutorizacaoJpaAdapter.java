package br.com.srportto.contratocommand.infrastructure.persistence;

import br.com.srportto.contratocommand.domain.model.Autorizacao;
import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
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
    private final AutorizacaoPersistenceMapper mapper;
    private final EntityManager entityManager;

    @Override
    public Autorizacao save(Autorizacao autorizacao) {
        if (autorizacao.getVersion() == null) {
            var entidade = mapper.paraEntidade(autorizacao);
            var salva = springDataRepository.save(entidade);
            return mapper.paraDominio(salva);
        }

        // Update: nunca montar+salvar uma instância nova com version não nulo (armadilha nº 11).
        // A entidade gerenciada é a mesma instância já carregada nesta transação (cache L1) —
        // aplicarEm + dirty-checking do Hibernate produzem o UPDATE ... AND version = ? no commit.
        var particaoAtual = ReversibleUUIDv7.extract(autorizacao.getIdAutorizacao());
        var entidadeGerenciada = entidadeGerenciadaObrigatoria(autorizacao.getIdAutorizacao(), particaoAtual);
        mapper.aplicarEm(autorizacao, entidadeGerenciada);
        return mapper.paraDominio(entidadeGerenciada);
    }

    @Override
    public Optional<Autorizacao> findById(UUID idAutorizacao) {
        var particao = ReversibleUUIDv7.extract(idAutorizacao);
        return springDataRepository.findByIdAutorizacaoAndParticao(idAutorizacao, particao)
                .map(mapper::paraDominio);
    }

    @Override
    public boolean existeAutorizacaoAtivaComIdEmpresa(UUID idUnicoContaContratante, String idAutorizacaoEmpresa) {
        var particao = IdContaUUIDPartitionDistributor.getPartitionFast(idUnicoContaContratante);
        return springDataRepository.existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa(particao, idAutorizacaoEmpresa);
    }

    @Override
    public Autorizacao transferirParaExpurgo(Autorizacao autorizacao, LocalDate dataReferenciaExpurgo) {
        var novaParticao = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(dataReferenciaExpurgo);
        var particaoAntiga = ReversibleUUIDv7.extract(autorizacao.getIdAutorizacao());
        var idAutorizacaoUuid = autorizacao.getIdAutorizacao();

        var entidadeGerenciada = entidadeGerenciadaObrigatoria(idAutorizacaoUuid, particaoAntiga);
        mapper.aplicarEm(autorizacao, entidadeGerenciada);

        if (novaParticao == particaoAntiga) {
            var salva = springDataRepository.save(entidadeGerenciada);
            return mapper.paraDominio(salva);
        }

        log.info("Transferindo autorização {} da partição {} para partição {}",
                idAutorizacaoUuid, particaoAntiga, novaParticao);

        // Passo 1: dirty-check do JPA monta UPDATE com AND version=? — é isso que protege contra
        // escrita concorrente de terceiro.
        var autorizacaoAtualizada = springDataRepository.saveAndFlush(entidadeGerenciada);

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

        return mapper.paraDominio(autorizacaoAtualizada);
    }

    private AutorizacaoJpaEntity entidadeGerenciadaObrigatoria(UUID idAutorizacao, int particao) {
        return springDataRepository.findByIdAutorizacaoAndParticao(idAutorizacao, particao)
                .orElseThrow(() -> new ObjectOptimisticLockingFailureException(Autorizacao.class, idAutorizacao,
                        "Autorização gerenciada não encontrada na partição " + particao + " para aplicar escrita", null));
    }

}
