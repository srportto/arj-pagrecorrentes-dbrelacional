package br.com.srportto.contratocommand.application.enabledproduct.pixauto.usecases;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.defaultservice.cancelamento.CancelamentoValidator;
import br.com.srportto.contratocommand.application.enabledproduct.pixauto.PixAutoRepository;
import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.utilities.ReversibleUUIDv7;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do CancelarPixAutoUseCase")
class CancelarPixAutoUseCaseTest {

    private static final int PARTICAO = 50;

    @Mock
    private PixAutoRepository repository;
    @Mock
    private CancelamentoValidator cancelamentoValidator;

    @InjectMocks
    private CancelarPixAutoUseCase useCase;

    @Test
    @DisplayName("cancela: marca status 5, registra cancelamento e persiste na nova partição")
    void cancela() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        CancelarAutorizacaoRequestDto request = TestFixtures.cancelarRequest(uuid.toString(), TipoProduto.PIX_AUTO);

        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(new IdAutorizacao(uuid, PARTICAO));
        aut.setTipoProduto(TipoProduto.PIX_AUTO);
        when(repository.findByIdAutorizacaoAndParticao(uuid, PARTICAO)).thenReturn(Optional.of(aut));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AutorizacaoCompletaResponseDto resp = useCase.execute(request);

        assertNotNull(resp);
        assertEquals(5, aut.getStatus());
        assertNotNull(aut.getCancelamento());
        assertEquals("C1", aut.getCancelamento().getCodigoCanalCancelamento());
        verify(cancelamentoValidator).validar(request);
        verify(repository).save(any());
    }

    @Test
    @DisplayName("lança BusinessException quando a autorização não é encontrada")
    void naoEncontrada() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        CancelarAutorizacaoRequestDto request = TestFixtures.cancelarRequest(uuid.toString(), TipoProduto.PIX_AUTO);
        when(repository.findByIdAutorizacaoAndParticao(uuid, PARTICAO)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> useCase.execute(request));
    }
}
