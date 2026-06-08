package br.com.srportto.contratocommand.application.enabledproduct.ddaauto.usecases;

import br.com.srportto.contratocommand.application.enabledproduct.ddaauto.DdaAutoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


import br.com.srportto.contratocommand.application.defaultservice.contratacao.ContratacaoValidator;
import br.com.srportto.contratocommand.application.enabledproduct.ddaauto.DdaAutoRepository;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class CriarDdaAutoUseCase {

    private static final Logger log = LoggerFactory.getLogger(CriarDdaAutoUseCase.class);

    private final DdaAutoRepository repository;
    private final DdaAutoMapper mapper;
    private final ContratacaoValidator contratacaoValidator;

    @Transactional
    public AutorizacaoCompletaResponseDto execute(CriarAutorizacaoRequest request) {
        log.info("Iniciando criação de autorização DDA para empresa: {}", request.idAutorizacaoEmpresa());

        contratacaoValidator.validar(request);

        var autorizacaoMontada = mapper.toDomain(request);
        var autorizadaPersistida = repository.save(autorizacaoMontada);

        log.info("Autorização DDA criada com sucesso. ID: {}, Empresa: {}", 
                autorizadaPersistida.getIdAutorizacao().getIdAutorizacao(),
                autorizadaPersistida.getIdAutorizacaoEmpresa());

        return AutorizacaoCompletaResponseDto.from(autorizadaPersistida);
    }
}