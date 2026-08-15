package br.com.srportto.contratocommand.application.contratacao;

import br.com.srportto.contratocommand.application.AutorizacaoMapper;
import br.com.srportto.contratocommand.application.AutorizacaoRepository;
import br.com.srportto.contratocommand.application.eventos.AutorizacaoPersistidaEvent;
import br.com.srportto.contratocommand.domain.utilities.IdContaUUIDPartitionDistributor;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.shared.exceptions.RecursoJaExisteException;
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
public class CriarAutorizacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(CriarAutorizacaoUseCase.class);

    private final AutorizacaoRepository repository;
    private final AutorizacaoMapper mapper;
    private final ContratacaoValidator contratacaoValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AutorizacaoCompletaResponseDto execute(ContratacaoContext context) {
        var dados = context.dados();
        log.info("Iniciando criação de autorização {} para empresa: {}",
                dados.tipoProduto(), dados.idAutorizacaoEmpresa());

        contratacaoValidator.validar(context);

        // Idempotência: mesmo escopo do índice único parcial uk_autorizacao_empresa_ativa no banco
        // (partições quentes, id_particao_conta < 900) — poda a busca para 1 partição em vez de
        // varrer todas.
        var idParticaoConta = IdContaUUIDPartitionDistributor.getPartitionFast(dados.idUnicoContaContratante());
        if (repository.existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa(idParticaoConta, dados.idAutorizacaoEmpresa())) {
            throw new RecursoJaExisteException("Autorização com id_autorizacao_empresa já existe: " + dados.idAutorizacaoEmpresa());
        }

        var autorizacaoMontada = mapper.toDomain(dados, context.tipoJornada());
        var autorizadaPersistida = repository.save(autorizacaoMontada);

        eventPublisher.publishEvent(new AutorizacaoPersistidaEvent(autorizadaPersistida));

        log.info("Autorização {} criada com sucesso. ID: {}, Empresa: {}",
                dados.tipoProduto(),
                autorizadaPersistida.getIdAutorizacao().getIdAutorizacao(),
                autorizadaPersistida.getIdAutorizacaoEmpresa());

        return AutorizacaoCompletaResponseDto.from(autorizadaPersistida);
    }

}
