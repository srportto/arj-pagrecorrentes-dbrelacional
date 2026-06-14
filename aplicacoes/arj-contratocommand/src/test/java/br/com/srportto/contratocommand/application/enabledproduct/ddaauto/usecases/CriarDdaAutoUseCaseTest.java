package br.com.srportto.contratocommand.application.enabledproduct.ddaauto.usecases;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.defaultservice.contratacao.ContratacaoValidator;
import br.com.srportto.contratocommand.application.enabledproduct.ddaauto.DdaAutoMapper;
import br.com.srportto.contratocommand.application.enabledproduct.ddaauto.DdaAutoRepository;
import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do CriarDdaAutoUseCase")
class CriarDdaAutoUseCaseTest {

    @Mock
    private DdaAutoRepository repository;
    @Mock
    private DdaAutoMapper mapper;
    @Mock
    private ContratacaoValidator contratacaoValidator;

    @InjectMocks
    private CriarDdaAutoUseCase useCase;

    @Test
    @DisplayName("valida, mapeia, persiste e retorna o DTO")
    void executa() {
        CriarAutorizacaoRequest request = TestFixtures.criarRequestDda();
        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(new IdAutorizacao(UUID.randomUUID(), 10));
        when(mapper.toDomain(request)).thenReturn(aut);
        when(repository.save(aut)).thenReturn(aut);

        AutorizacaoCompletaResponseDto resp = useCase.execute(request);

        assertNotNull(resp);
        assertEquals(aut.getIdAutorizacao().getIdAutorizacao(), resp.getIdAutorizacao());
        verify(contratacaoValidator).validar(request);
        verify(repository).save(aut);
    }
}
