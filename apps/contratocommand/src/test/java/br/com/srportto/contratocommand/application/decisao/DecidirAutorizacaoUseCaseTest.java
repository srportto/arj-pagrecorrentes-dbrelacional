package br.com.srportto.contratocommand.application.decisao;

import br.com.srportto.contratocommand.application.AutorizacaoRepository;
import br.com.srportto.contratocommand.application.ExpurgoAutorizacaoService;
import br.com.srportto.contratocommand.application.decisao.rules.AcaoDecisaoValida;
import br.com.srportto.contratocommand.application.decisao.rules.TipoProdutoDecisao;
import br.com.srportto.contratocommand.application.decisao.rules.TransicaoValidaDecisao;
import br.com.srportto.contratocommand.application.eventos.AutorizacaoPersistidaEvent;
import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.utilities.ReversibleUUIDv7;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.DecisaoAutorizacaoRequest;
import br.com.srportto.contratocommand.shared.exceptions.ApplicationException;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do DecidirAutorizacaoUseCase")
class DecidirAutorizacaoUseCaseTest {

    private static final int PARTICAO = 50;

    @Mock
    private AutorizacaoRepository repository;
    @Mock
    private DecisaoValidator decisaoValidator;
    @Mock
    private ExpurgoAutorizacaoService expurgoAutorizacaoService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DecidirAutorizacaoUseCase useCase;

    private Autorizacao autorizacaoRecebida(UUID uuid) {
        var aut = new Autorizacao();
        aut.setIdAutorizacao(new IdAutorizacao(uuid, PARTICAO));
        aut.setTipoProduto(TipoProduto.PIX_AUTO);
        aut.setStatus((int) StatusAutorizacao.RECEBIDA.getStatusAutorizacao());
        aut.setMotivoStatus("RECEPCAO_SPI_J1");
        return aut;
    }

    private DecisaoContext contexto(UUID uuid, String acao) {
        return DecisaoContext.doRequest(uuid.toString(), TipoProduto.PIX_AUTO,
                new DecisaoAutorizacaoRequest(acao, "C1", null));
    }

    @Test
    @DisplayName("APROVAR grava ATIVA com motivo AUTORIZACAO_ACEITA_POR_TODOS, salva direto (sem expurgo) e publica um evento")
    void aprova() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        Autorizacao aut = autorizacaoRecebida(uuid);
        when(repository.findByIdAutorizacaoAndParticao(uuid, PARTICAO)).thenReturn(Optional.of(aut));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AutorizacaoCompletaResponseDto resp = useCase.execute(contexto(uuid, "APROVAR"));

        assertNotNull(resp);
        assertEquals(4, aut.getStatus()); // StatusAutorizacao.ATIVA
        assertEquals("AUTORIZACAO_ACEITA_POR_TODOS", aut.getMotivoStatus());
        verify(repository).save(aut);
        verify(expurgoAutorizacaoService, never()).transferirParaExpurgo(any(), any());
        verify(eventPublisher).publishEvent(new AutorizacaoPersistidaEvent(aut));
    }

    @Test
    @DisplayName("REJEITAR grava REJEITADA com motivo REJEITADA_PAGADOR e transfere para a partição de expurgo")
    void rejeita() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        Autorizacao aut = autorizacaoRecebida(uuid);
        when(repository.findByIdAutorizacaoAndParticao(uuid, PARTICAO)).thenReturn(Optional.of(aut));
        when(expurgoAutorizacaoService.transferirParaExpurgo(eq(aut), any(LocalDate.class))).thenReturn(aut);

        useCase.execute(contexto(uuid, "REJEITAR"));

        assertEquals(6, aut.getStatus()); // StatusAutorizacao.REJEITADA
        assertEquals("REJEITADA_PAGADOR", aut.getMotivoStatus());
        verify(expurgoAutorizacaoService).transferirParaExpurgo(eq(aut), any(LocalDate.class));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("EXPIRAR grava REJEITADA com motivo REJEITADA_SISTEMA_TIMEOUT_J1 e transfere para a partição de expurgo")
    void expira() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        Autorizacao aut = autorizacaoRecebida(uuid);
        when(repository.findByIdAutorizacaoAndParticao(uuid, PARTICAO)).thenReturn(Optional.of(aut));
        when(expurgoAutorizacaoService.transferirParaExpurgo(eq(aut), any(LocalDate.class))).thenReturn(aut);

        useCase.execute(contexto(uuid, "EXPIRAR"));

        assertEquals(6, aut.getStatus()); // StatusAutorizacao.REJEITADA
        assertEquals("REJEITADA_SISTEMA_TIMEOUT_J1", aut.getMotivoStatus());
        verify(expurgoAutorizacaoService).transferirParaExpurgo(eq(aut), any(LocalDate.class));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("lança BusinessException quando a autorização não é encontrada e não publica evento")
    void naoEncontrada() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        when(repository.findByIdAutorizacaoAndParticao(uuid, PARTICAO)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> useCase.execute(contexto(uuid, "APROVAR")));

        verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
    }

    @Test
    @DisplayName("encapsula exceção de repository em ApplicationException preservando a causa")
    void encapsulaExcecaoRepositoryComCausa() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);

        RuntimeException causaOriginal = new RuntimeException("Erro de acesso ao banco de dados");
        when(repository.findByIdAutorizacaoAndParticao(uuid, PARTICAO)).thenThrow(causaOriginal);

        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> useCase.execute(contexto(uuid, "APROVAR")));

        assertNotNull(ex.getCause());
        assertSame(causaOriginal, ex.getCause());
        verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
    }

    /** Testes com validação REAL (rules de verdade), para exercitar a idempotência ponta a ponta. */
    @org.junit.jupiter.api.Nested
    @DisplayName("Idempotência (validator real, sem mock de regras)")
    class ComValidacaoReal {

        private DecidirAutorizacaoUseCase useCaseComValidacaoReal;

        @BeforeEach
        void setUp() {
            var validatorReal = new DecisaoValidator(
                    List.of(new AcaoDecisaoValida(), new TipoProdutoDecisao(), new TransicaoValidaDecisao()));
            useCaseComValidacaoReal = new DecidirAutorizacaoUseCase(
                    repository, validatorReal, expurgoAutorizacaoService, eventPublisher);
        }

        @Test
        @DisplayName("decisão sobre autorização já ATIVA (não RECEBIDA) é 422 e não altera a linha nem publica evento")
        void statusJaResolvidoNaoAltera() {
            UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
            var aut = autorizacaoRecebida(uuid);
            aut.setStatus((int) StatusAutorizacao.ATIVA.getStatusAutorizacao());
            aut.setMotivoStatus("AUTORIZACAO_ACEITA_POR_TODOS");
            when(repository.findByIdAutorizacaoAndParticao(uuid, PARTICAO)).thenReturn(Optional.of(aut));

            assertThrows(BusinessException.class,
                    () -> useCaseComValidacaoReal.execute(contexto(uuid, "EXPIRAR")));

            assertEquals(4, aut.getStatus()); // permanece ATIVA
            verify(repository, never()).save(any());
            verify(expurgoAutorizacaoService, never()).transferirParaExpurgo(any(), any());
            verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
        }

        @Test
        @DisplayName("decisão repetida: primeira chamada aplica, segunda é 422 (um evento no total)")
        void decisaoRepetidaPublicaUmUnicoEvento() {
            UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
            var aut = autorizacaoRecebida(uuid);
            when(repository.findByIdAutorizacaoAndParticao(uuid, PARTICAO)).thenReturn(Optional.of(aut));
            when(expurgoAutorizacaoService.transferirParaExpurgo(eq(aut), any(LocalDate.class))).thenReturn(aut);

            useCaseComValidacaoReal.execute(contexto(uuid, "EXPIRAR"));
            assertEquals(6, aut.getStatus()); // agora REJEITADA

            assertThrows(BusinessException.class,
                    () -> useCaseComValidacaoReal.execute(contexto(uuid, "EXPIRAR")));

            verify(eventPublisher, org.mockito.Mockito.times(1))
                    .publishEvent(any(AutorizacaoPersistidaEvent.class));
        }

        @Test
        @DisplayName("acao desconhecida lança BusinessException")
        void acaoDesconhecida() {
            UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
            var aut = autorizacaoRecebida(uuid);
            when(repository.findByIdAutorizacaoAndParticao(uuid, PARTICAO)).thenReturn(Optional.of(aut));

            assertThrows(BusinessException.class,
                    () -> useCaseComValidacaoReal.execute(contexto(uuid, "CONFIRMAR")));

            verify(repository, never()).save(any());
            verify(expurgoAutorizacaoService, never()).transferirParaExpurgo(any(), any());
        }
    }
}
