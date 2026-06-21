package br.com.srportto.contratocommand.application.autorizacao.usecases;

import br.com.srportto.contratocommand.application.autorizacao.AutorizacaoMapper;
import br.com.srportto.contratocommand.application.autorizacao.AutorizacaoRepository;
import br.com.srportto.contratocommand.application.defaultservice.contratacao.ContratacaoValidator;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso único de criação de autorização, compartilhado por todos os produtos. A variação
 * por produto vive nas regras de negócio ({@link ContratacaoValidator}) e nas strategies, não aqui.
 */
@Component
@AllArgsConstructor
public class CriarAutorizacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(CriarAutorizacaoUseCase.class);

    private final AutorizacaoRepository repository;
    private final AutorizacaoMapper mapper;
    private final ContratacaoValidator contratacaoValidator;

    @Transactional
    public AutorizacaoCompletaResponseDto execute(CriarAutorizacaoRequest request) {
        log.info("Iniciando criação de autorização {} para empresa: {}",
                request.tipoProduto(), request.idAutorizacaoEmpresa());

        contratacaoValidator.validar(request);

        var autorizacaoMontada = mapper.toDomain(request);
        var autorizadaPersistida = repository.save(autorizacaoMontada);

        log.info("Autorização {} criada com sucesso. ID: {}, Empresa: {}",
                request.tipoProduto(),
                autorizadaPersistida.getIdAutorizacao().getIdAutorizacao(),
                autorizadaPersistida.getIdAutorizacaoEmpresa());

        return AutorizacaoCompletaResponseDto.from(autorizadaPersistida);
    }

}
