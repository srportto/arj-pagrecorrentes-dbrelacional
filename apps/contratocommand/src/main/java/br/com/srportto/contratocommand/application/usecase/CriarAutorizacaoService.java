package br.com.srportto.contratocommand.application.usecase;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.event.AutorizacaoPersistidaEvent;
import br.com.srportto.contratocommand.domain.exception.RecursoJaExisteException;
import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import br.com.srportto.contratocommand.domain.service.contratacao.ContratacaoValidator;
import br.com.srportto.contratocommand.domain.utilities.IdContaUUIDPartitionDistributor;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso único de criação de autorização, compartilhado por todos os produtos. A variação
 * por produto vive nas regras de negócio ({@link ContratacaoValidator}), não aqui.
 */
@Service
@AllArgsConstructor
public class CriarAutorizacaoService implements CriarAutorizacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(CriarAutorizacaoService.class);

    private final AutorizacaoRepository repository;
    private final AutorizacaoMapper mapper;
    private final ContratacaoValidator contratacaoValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Autorizacao execute(CriarAutorizacaoCommand command) {
        log.info("Iniciando criação de autorização {} para empresa: {}",
                command.tipoProduto(), command.idAutorizacaoEmpresa());

        contratacaoValidator.validar(command);

        // Idempotência: mesmo escopo do índice único parcial uk_autorizacao_empresa_ativa no banco
        // (partições quentes, id_particao_conta < 900) — poda a busca para 1 partição em vez de
        // varrer todas.
        var idParticaoConta = IdContaUUIDPartitionDistributor.getPartitionFast(command.idUnicoContaContratante());
        if (repository.existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa(idParticaoConta, command.idAutorizacaoEmpresa())) {
            throw new RecursoJaExisteException("Autorização com id_autorizacao_empresa já existe: " + command.idAutorizacaoEmpresa());
        }

        var autorizacaoMontada = mapper.toDomain(command);
        var autorizadaPersistida = repository.save(autorizacaoMontada);

        eventPublisher.publishEvent(new AutorizacaoPersistidaEvent(autorizadaPersistida));

        log.info("Autorização {} criada com sucesso. ID: {}, Empresa: {}",
                command.tipoProduto(),
                autorizadaPersistida.getIdAutorizacao().getIdAutorizacao(),
                autorizadaPersistida.getIdAutorizacaoEmpresa());

        return autorizadaPersistida;
    }

}
