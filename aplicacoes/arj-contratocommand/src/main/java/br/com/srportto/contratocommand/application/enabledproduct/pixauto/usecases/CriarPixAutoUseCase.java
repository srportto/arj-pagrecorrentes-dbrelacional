package br.com.srportto.contratocommand.application.enabledproduct.pixauto.usecases;

import br.com.srportto.contratocommand.application.defaultservice.contratacao.ContratacaoValidator;
import br.com.srportto.contratocommand.application.enabledproduct.pixauto.PixAutoMapper;
import br.com.srportto.contratocommand.application.enabledproduct.pixauto.PixAutoRepository;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Component
@AllArgsConstructor
public class CriarPixAutoUseCase {

    private static final Logger log = LoggerFactory.getLogger(CriarPixAutoUseCase.class);

    private final PixAutoRepository repository;
    private final PixAutoMapper mapper;
    private final ContratacaoValidator contratacaoValidator;

    @Transactional
    public AutorizacaoCompletaResponseDto execute(CriarAutorizacaoRequest request) {
        log.info("Iniciando criação de autorizacao pix-auto para empresa: {}", request.idAutorizacaoEmpresa());

        contratacaoValidator.validar(request);

        var autorizacaoMontada = mapper.toDomain(request);
        var autorizadaPersistida = repository.save(autorizacaoMontada);

        log.info("Autorização Pix criada com sucesso. ID: {}, Empresa: {}",
                autorizadaPersistida.getIdAutorizacao().getIdAutorizacao(), 
                autorizadaPersistida.getIdAutorizacaoEmpresa());

        return AutorizacaoCompletaResponseDto.from(autorizadaPersistida);
    }


}