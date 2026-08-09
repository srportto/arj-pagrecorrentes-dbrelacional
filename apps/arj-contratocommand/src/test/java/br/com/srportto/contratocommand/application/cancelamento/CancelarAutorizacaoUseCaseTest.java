package br.com.srportto.contratocommand.application.cancelamento;

import br.com.srportto.contratocommand.application.AutorizacaoRepository;
import br.com.srportto.contratocommand.application.ExpurgoAutorizacaoService;
import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.eventos.AutorizacaoPersistidaEvent;
import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.utilities.ReversibleUUIDv7;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do CancelarAutorizacaoUseCase")
class CancelarAutorizacaoUseCaseTest {

    private static final int PARTICAO = 50;

    @Mock
    private AutorizacaoRepository repository;
    @Mock
    private CancelamentoValidator cancelamentoValidator;
    @Mock
    private ExpurgoAutorizacaoService expurgoAutorizacaoService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CancelarAutorizacaoUseCase useCase;

    @Test
    @DisplayName("cancela: marca status 5, registra cancelamento e delega a transferência de partição ao serviço de expurgo")
    void cancela() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        CancelamentoContext request = TestFixtures.cancelarContext(uuid.toString(), TipoProduto.PIX_AUTO);

        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(new IdAutorizacao(uuid, PARTICAO));
        aut.setTipoProduto(TipoProduto.PIX_AUTO);
        when(repository.findByIdAutorizacaoAndParticao(uuid, PARTICAO)).thenReturn(Optional.of(aut));
        when(expurgoAutorizacaoService.transferirParaExpurgo(eq(aut), any(LocalDate.class))).thenReturn(aut);

        AutorizacaoCompletaResponseDto resp = useCase.execute(request);

        assertNotNull(resp);
        assertEquals(5, aut.getStatus());
        assertNotNull(aut.getCancelamento());
        assertEquals("C1", aut.getCancelamento().getCodigoCanalCancelamento());
        verify(cancelamentoValidator).validar(any(CancelamentoContext.class));
        verify(expurgoAutorizacaoService).transferirParaExpurgo(eq(aut), any(LocalDate.class));

        verify(eventPublisher).publishEvent(new AutorizacaoPersistidaEvent(aut));
    }

    @Test
    @DisplayName("lança BusinessException quando a autorização não é encontrada e não publica evento")
    void naoEncontrada() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        CancelamentoContext request = TestFixtures.cancelarContext(uuid.toString(), TipoProduto.PIX_AUTO);
        when(repository.findByIdAutorizacaoAndParticao(uuid, PARTICAO)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> useCase.execute(request));

        verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
    }

    @Test
    @DisplayName("propaga a exceção quando o serviço de expurgo falha (limite transacional do execute garante rollback) e não publica evento")
    void rollbackQuandoExpurgoFalha() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        CancelamentoContext request = TestFixtures.cancelarContext(uuid.toString(), TipoProduto.PIX_AUTO);

        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(new IdAutorizacao(uuid, PARTICAO));
        aut.setTipoProduto(TipoProduto.PIX_AUTO);
        when(repository.findByIdAutorizacaoAndParticao(uuid, PARTICAO)).thenReturn(Optional.of(aut));
        when(expurgoAutorizacaoService.transferirParaExpurgo(eq(aut), any(LocalDate.class)))
                .thenThrow(new RuntimeException("falha ao reinserir na nova particao"));

        // A exceção propaga para fora do execute() (anotado com @Transactional), o container
        // faz rollback de qualquer alteração já aplicada na transação.
        assertThrows(RuntimeException.class, () -> useCase.execute(request));
        verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
    }
}
