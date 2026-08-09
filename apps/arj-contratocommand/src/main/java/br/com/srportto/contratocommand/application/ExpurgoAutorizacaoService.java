package br.com.srportto.contratocommand.application;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.utilities.ControleExpurgoAutorizacao;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

        repository.deleteById(autorizacao.getIdAutorizacao());

        // Dentro do mesmo persistence context (@Transactional no use case chamador), o JPA não
        // permite alterar o @EmbeddedId de uma entidade gerenciada nem fazer merge de uma
        // instância já removida (ObjectDeletedException). O flush executa o DELETE imediatamente
        // e o detach desanexa a instância, permitindo reaproveitá-la como nova linha na partição
        // de expurgo.
        repository.flush();
        entityManager.detach(autorizacao);

        autorizacao.getIdAutorizacao().setIdParticaoConta(novaParticao);
        return repository.save(autorizacao);
    }
}
