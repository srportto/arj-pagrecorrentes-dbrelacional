package br.com.srportto.contratocommand.application.usecase;

import br.com.srportto.contratocommand.domain.port.in.CancelarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.service.cancelamento.CancelamentoValidator;
import br.com.srportto.contratocommand.domain.service.cancelamento.rules.ProdutoSuportadoCancelamento;
import br.com.srportto.contratocommand.domain.service.cancelamento.rules.TipoProdutoCancelamento;
import br.com.srportto.contratocommand.domain.service.cancelamento.rules.TransicaoStatusValida;
import br.com.srportto.contratocommand.domain.event.AutorizacaoPersistidaEvent;
import br.com.srportto.contratocommand.domain.model.Autorizacao;
import br.com.srportto.contratocommand.domain.model.AutorizacaoId;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.infrastructure.persistence.ReversibleUUIDv7;
import br.com.srportto.contratocommand.domain.exception.ApplicationException;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do CancelarAutorizacaoService")
class CancelarAutorizacaoServiceTest {

    private static final int PARTICAO = 50;

    @Mock
    private AutorizacaoRepository repository;
    @Mock
    private CancelamentoValidator cancelamentoValidator;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private CarregadorAutorizacao carregadorAutorizacao;

    @InjectMocks
    private CancelarAutorizacaoService service;

    @Test
    @DisplayName("cancela: marca status 5, registra cancelamento e delega a transferência de partição ao serviço de expurgo")
    void cancela() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        CancelarAutorizacaoCommand command = TestFixtures.cancelarContext(uuid.toString(), TipoProduto.PIX_AUTO);

        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(uuid);
        aut.setIdParticaoConta(PARTICAO);
        aut.setTipoProduto(TipoProduto.PIX_AUTO);
        aut.setStatus((int) StatusAutorizacao.ATIVA.getStatusAutorizacao());
        when(carregadorAutorizacao.carregar(any(AutorizacaoId.class))).thenReturn(aut);
        when(repository.transferirParaExpurgo(eq(aut), any(LocalDate.class))).thenReturn(aut);

        Autorizacao resp = service.execute(command);

        assertNotNull(resp);
        assertEquals(5, aut.getStatus());
        assertNotNull(aut.getCancelamento());
        assertEquals("C1", aut.getCancelamento().getCodigoCanalCancelamento());
        verify(cancelamentoValidator).validar(any(CancelarAutorizacaoCommand.class));
        verify(repository).transferirParaExpurgo(eq(aut), any(LocalDate.class));

        verify(eventPublisher).publishEvent(new AutorizacaoPersistidaEvent(aut));
    }

    @Test
    @DisplayName("propaga BusinessException lançada pelo carregador quando a autorização não é encontrada, sem publicar evento")
    void naoEncontrada() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        CancelarAutorizacaoCommand command = TestFixtures.cancelarContext(uuid.toString(), TipoProduto.PIX_AUTO);
        when(carregadorAutorizacao.carregar(any(AutorizacaoId.class)))
                .thenThrow(new BusinessException("Autorização não encontrada com ID: " + uuid));

        assertThrows(BusinessException.class, () -> service.execute(command));

        verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
    }

    @Test
    @DisplayName("propaga a exceção quando o serviço de expurgo falha (limite transacional do execute garante rollback) e não publica evento")
    void rollbackQuandoExpurgoFalha() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        CancelarAutorizacaoCommand command = TestFixtures.cancelarContext(uuid.toString(), TipoProduto.PIX_AUTO);

        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(uuid);
        aut.setIdParticaoConta(PARTICAO);
        aut.setTipoProduto(TipoProduto.PIX_AUTO);
        aut.setStatus((int) StatusAutorizacao.ATIVA.getStatusAutorizacao());
        when(carregadorAutorizacao.carregar(any(AutorizacaoId.class))).thenReturn(aut);
        when(repository.transferirParaExpurgo(eq(aut), any(LocalDate.class)))
                .thenThrow(new RuntimeException("falha ao reinserir na nova particao"));

        // execute() é @Transactional: exceção propagada faz o container fazer rollback.
        assertThrows(RuntimeException.class, () -> service.execute(command));
        verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
    }

    @Test
    @DisplayName("propaga a exceção do carregador sem reembalar (fonte única de carregamento, design.md D2)")
    void propagaExcecaoDoCarregador() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        CancelarAutorizacaoCommand command = TestFixtures.cancelarContext(uuid.toString(), TipoProduto.PIX_AUTO);

        ApplicationException causaOriginal = new ApplicationException("Falha ao obter autorização " + uuid, new RuntimeException("erro de banco"));
        when(carregadorAutorizacao.carregar(any(AutorizacaoId.class))).thenThrow(causaOriginal);

        ApplicationException ex = assertThrows(ApplicationException.class, () -> service.execute(command));

        assertSame(causaOriginal, ex);
        verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
    }

    @Test
    @DisplayName("conflito de concorrência no carregamento propaga sem virar ApplicationException (design.md, D3 — resulta em 409, não 500)")
    void propagaConflitoDeConcorrenciaNoCarregamento() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        CancelarAutorizacaoCommand command = TestFixtures.cancelarContext(uuid.toString(), TipoProduto.PIX_AUTO);

        OptimisticLockingFailureException conflito = new OptimisticLockingFailureException("versão divergente");
        when(carregadorAutorizacao.carregar(any(AutorizacaoId.class))).thenThrow(conflito);

        OptimisticLockingFailureException ex = assertThrows(OptimisticLockingFailureException.class,
                () -> service.execute(command));

        assertSame(conflito, ex);
        verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
    }

    /** Testes com validação REAL (rules de verdade), para exercitar TransicaoStatusValida ponta a ponta. */
    @org.junit.jupiter.api.Nested
    @DisplayName("Idempotência (validator real, sem mock de regras)")
    class ComValidacaoReal {

        private CancelarAutorizacaoService useCaseComValidacaoReal;

        @BeforeEach
        void setUp() {
            var validatorReal = new CancelamentoValidator(
                    List.of(new ProdutoSuportadoCancelamento(), new TipoProdutoCancelamento(), new TransicaoStatusValida()));
            useCaseComValidacaoReal = new CancelarAutorizacaoService(
                    repository, validatorReal, eventPublisher, carregadorAutorizacao);
        }

        @Test
        @DisplayName("cancelar autorização já CANCELADA é erro de negócio e não publica evento")
        void autorizacaoJaCanceladaNaoPublicaEvento() {
            UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
            CancelarAutorizacaoCommand command = TestFixtures.cancelarContext(uuid.toString(), TipoProduto.PIX_AUTO);

            Autorizacao aut = new Autorizacao();
            aut.setIdAutorizacao(uuid);
            aut.setIdParticaoConta(PARTICAO);
            aut.setTipoProduto(TipoProduto.PIX_AUTO);
            aut.setStatus((int) StatusAutorizacao.CANCELADA.getStatusAutorizacao());
            when(carregadorAutorizacao.carregar(any(AutorizacaoId.class))).thenReturn(aut);

            assertThrows(BusinessException.class, () -> useCaseComValidacaoReal.execute(command));

            assertEquals(5, aut.getStatus()); // permanece CANCELADA
            verify(repository, never()).transferirParaExpurgo(any(), any());
            verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
        }

        @Test
        @DisplayName("cancelar autorização ATIVA com validator real tem sucesso")
        void autorizacaoAtivaCancelaComSucesso() {
            UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
            CancelarAutorizacaoCommand command = TestFixtures.cancelarContext(uuid.toString(), TipoProduto.PIX_AUTO);

            Autorizacao aut = new Autorizacao();
            aut.setIdAutorizacao(uuid);
            aut.setIdParticaoConta(PARTICAO);
            aut.setTipoProduto(TipoProduto.PIX_AUTO);
            aut.setStatus((int) StatusAutorizacao.ATIVA.getStatusAutorizacao());
            when(carregadorAutorizacao.carregar(any(AutorizacaoId.class))).thenReturn(aut);
            when(repository.transferirParaExpurgo(eq(aut), any(LocalDate.class))).thenReturn(aut);

            useCaseComValidacaoReal.execute(command);

            assertEquals(5, aut.getStatus());
            verify(eventPublisher).publishEvent(any(AutorizacaoPersistidaEvent.class));
        }
    }
}
