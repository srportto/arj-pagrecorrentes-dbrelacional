package br.com.srportto.contratocommand.application.usecase;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.event.AutorizacaoPersistidaEvent;
import br.com.srportto.contratocommand.domain.exception.ApplicationException;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import br.com.srportto.contratocommand.domain.model.Autorizacao;
import br.com.srportto.contratocommand.domain.model.AutorizacaoId;
import br.com.srportto.contratocommand.domain.port.in.AtualizarDadosRecorrenciaCommand;
import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import br.com.srportto.contratocommand.domain.service.atualizacao.AtualizacaoValidator;
import br.com.srportto.contratocommand.domain.service.atualizacao.rules.DataFimVigenciaInvalidaAtualizacao;
import br.com.srportto.contratocommand.domain.service.atualizacao.rules.StatusPermiteAtualizacao;
import br.com.srportto.contratocommand.domain.service.atualizacao.rules.TipoProdutoAtualizacao;
import br.com.srportto.contratocommand.domain.service.atualizacao.rules.ValorLimiteAtualizacaoInvalido;
import br.com.srportto.contratocommand.infrastructure.persistence.ReversibleUUIDv7;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do AtualizarDadosRecorrenciaService")
class AtualizarDadosRecorrenciaServiceTest {

    private static final int PARTICAO = 50;

    @Mock
    private AutorizacaoRepository repository;
    @Mock
    private AtualizacaoValidator atualizacaoValidator;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private CarregadorAutorizacao carregadorAutorizacao;

    @InjectMocks
    private AtualizarDadosRecorrenciaService service;

    private Autorizacao autorizacaoAtiva(UUID uuid) {
        var aut = new Autorizacao();
        aut.setIdAutorizacao(uuid);
        aut.setIdParticaoConta(PARTICAO);
        aut.setTipoProduto(TipoProduto.PIX_AUTO);
        aut.setStatus((int) StatusAutorizacao.ATIVA.getStatusAutorizacao());
        aut.setValorLimite(new BigDecimal("1000.00"));
        aut.setDataFimVigencia(LocalDate.now().plusDays(30));
        aut.setIndicadorUsoLimiteConta((short) 0);
        aut.setQuantidadeDividasCiclo((short) 2);
        return aut;
    }

    @Test
    @DisplayName("atualiza somente o campo informado e publica evento com o estado final")
    void atualizaCampoIsolado() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        var command = new AtualizarDadosRecorrenciaCommand(AutorizacaoId.de(uuid.toString()), TipoProduto.PIX_AUTO, null, null,
                new BigDecimal("5000.00"), null, null, null, "C1", UUID.randomUUID());

        Autorizacao aut = autorizacaoAtiva(uuid);
        when(carregadorAutorizacao.carregar(any(AutorizacaoId.class))).thenReturn(aut);
        when(repository.save(aut)).thenReturn(aut);

        Autorizacao resp = service.execute(command);

        assertNotNull(resp);
        assertEquals(new BigDecimal("5000.00"), aut.getValorLimite());
        assertEquals(LocalDate.now().plusDays(30), aut.getDataFimVigencia()); // não alterado
        assertEquals((short) 0, aut.getIndicadorUsoLimiteConta()); // não alterado
        assertEquals((short) 2, aut.getQuantidadeDividasCiclo()); // não alterado
        verify(atualizacaoValidator).validar(any(AtualizarDadosRecorrenciaCommand.class));
        verify(eventPublisher).publishEvent(new AutorizacaoPersistidaEvent(aut));
    }

    @Test
    @DisplayName("atualiza todos os campos informados")
    void atualizaTodosOsCampos() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        var novaData = LocalDate.now().plusDays(90);
        var command = new AtualizarDadosRecorrenciaCommand(AutorizacaoId.de(uuid.toString()), TipoProduto.PIX_AUTO, null, null,
                new BigDecimal("7000.00"), novaData, 1, 5, "C1", UUID.randomUUID());

        Autorizacao aut = autorizacaoAtiva(uuid);
        when(carregadorAutorizacao.carregar(any(AutorizacaoId.class))).thenReturn(aut);
        when(repository.save(aut)).thenReturn(aut);

        service.execute(command);

        assertEquals(new BigDecimal("7000.00"), aut.getValorLimite());
        assertEquals(novaData, aut.getDataFimVigencia());
        assertEquals((short) 1, aut.getIndicadorUsoLimiteConta());
        assertEquals((short) 5, aut.getQuantidadeDividasCiclo());
    }

    @Test
    @DisplayName("nenhum campo informado não altera nenhum dado, mas atualiza dataHoraUltimaAtualizacao")
    void nenhumCampoInformadoNaoAltera() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        var command = new AtualizarDadosRecorrenciaCommand(AutorizacaoId.de(uuid.toString()), TipoProduto.PIX_AUTO, null, null,
                null, null, null, null, "C1", UUID.randomUUID());

        Autorizacao aut = autorizacaoAtiva(uuid);
        var valorLimiteOriginal = aut.getValorLimite();
        var dataFimVigenciaOriginal = aut.getDataFimVigencia();
        when(carregadorAutorizacao.carregar(any(AutorizacaoId.class))).thenReturn(aut);
        when(repository.save(aut)).thenReturn(aut);

        service.execute(command);

        assertEquals(valorLimiteOriginal, aut.getValorLimite());
        assertEquals(dataFimVigenciaOriginal, aut.getDataFimVigencia());
        assertNotNull(aut.getDataHoraUltimaAtualizacao());
    }

    @Test
    @DisplayName("propaga BusinessException lançada pelo carregador quando a autorização não é encontrada, sem publicar evento")
    void naoEncontrada() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        AtualizarDadosRecorrenciaCommand command = TestFixtures.atualizarContext(uuid.toString(), TipoProduto.PIX_AUTO);
        when(carregadorAutorizacao.carregar(any(AutorizacaoId.class)))
                .thenThrow(new BusinessException("Autorização não encontrada com ID: " + uuid));

        assertThrows(BusinessException.class, () -> service.execute(command));

        verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
    }

    @Test
    @DisplayName("propaga a exceção do carregador sem reembalar (fonte única de carregamento, design.md D2)")
    void propagaExcecaoDoCarregador() {
        UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
        AtualizarDadosRecorrenciaCommand command = TestFixtures.atualizarContext(uuid.toString(), TipoProduto.PIX_AUTO);

        ApplicationException causaOriginal = new ApplicationException("Falha ao obter autorização " + uuid, new RuntimeException("erro de banco"));
        when(carregadorAutorizacao.carregar(any(AutorizacaoId.class))).thenThrow(causaOriginal);

        ApplicationException ex = assertThrows(ApplicationException.class, () -> service.execute(command));

        assertSame(causaOriginal, ex);
        verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
    }

    /** Testes com validação REAL (rules de verdade), para exercitar as 4 rules ponta a ponta. */
    @org.junit.jupiter.api.Nested
    @DisplayName("Com validação real (sem mock de regras)")
    class ComValidacaoReal {

        private AtualizarDadosRecorrenciaService useCaseComValidacaoReal;

        @BeforeEach
        void setUp() {
            var validatorReal = new AtualizacaoValidator(List.of(
                    new TipoProdutoAtualizacao(), new StatusPermiteAtualizacao(),
                    new DataFimVigenciaInvalidaAtualizacao(), new ValorLimiteAtualizacaoInvalido()));
            useCaseComValidacaoReal = new AtualizarDadosRecorrenciaService(
                    repository, validatorReal, eventPublisher, carregadorAutorizacao);
        }

        @Test
        @DisplayName("autorização RECEBIDA rejeitada com BusinessException e sem publicar evento")
        void autorizacaoNaoAtivaNaoPublicaEvento() {
            UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
            AtualizarDadosRecorrenciaCommand command = TestFixtures.atualizarContext(uuid.toString(), TipoProduto.PIX_AUTO);

            Autorizacao aut = autorizacaoAtiva(uuid);
            aut.setStatus((int) StatusAutorizacao.RECEBIDA.getStatusAutorizacao());
            when(carregadorAutorizacao.carregar(any(AutorizacaoId.class))).thenReturn(aut);

            assertThrows(BusinessException.class, () -> useCaseComValidacaoReal.execute(command));

            verify(repository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
        }

        @Test
        @DisplayName("produto do header divergente rejeitado com BusinessException")
        void produtoDivergenteNaoPublicaEvento() {
            UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
            AtualizarDadosRecorrenciaCommand command = TestFixtures.atualizarContext(uuid.toString(), TipoProduto.DDA_AUTO);

            Autorizacao aut = autorizacaoAtiva(uuid); // PIX_AUTO persistido
            when(carregadorAutorizacao.carregar(any(AutorizacaoId.class))).thenReturn(aut);

            assertThrows(BusinessException.class, () -> useCaseComValidacaoReal.execute(command));

            verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
        }

        @Test
        @DisplayName("dataFimVigencia no passado rejeitada com BusinessException")
        void dataFimVigenciaPassadoNaoPublicaEvento() {
            UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
            var command = new AtualizarDadosRecorrenciaCommand(AutorizacaoId.de(uuid.toString()), TipoProduto.PIX_AUTO, null, null,
                    null, LocalDate.now().minusDays(1), null, null, "C1", UUID.randomUUID());

            Autorizacao aut = autorizacaoAtiva(uuid);
            when(carregadorAutorizacao.carregar(any(AutorizacaoId.class))).thenReturn(aut);

            assertThrows(BusinessException.class, () -> useCaseComValidacaoReal.execute(command));

            verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
        }

        @Test
        @DisplayName("valorLimite zero ou negativo rejeitado com BusinessException")
        void valorLimiteInvalidoNaoPublicaEvento() {
            UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
            var command = new AtualizarDadosRecorrenciaCommand(AutorizacaoId.de(uuid.toString()), TipoProduto.PIX_AUTO, null, null,
                    BigDecimal.ZERO, null, null, null, "C1", UUID.randomUUID());

            Autorizacao aut = autorizacaoAtiva(uuid);
            when(carregadorAutorizacao.carregar(any(AutorizacaoId.class))).thenReturn(aut);

            assertThrows(BusinessException.class, () -> useCaseComValidacaoReal.execute(command));

            verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
        }

        @Test
        @DisplayName("autorização ATIVA com dados válidos atualiza e publica evento com status ATIVA (tipoEvento=ATIVACAO)")
        void autorizacaoAtivaAtualizaComSucesso() {
            UUID uuid = ReversibleUUIDv7.generate(PARTICAO);
            AtualizarDadosRecorrenciaCommand command = TestFixtures.atualizarContext(uuid.toString(), TipoProduto.PIX_AUTO);

            Autorizacao aut = autorizacaoAtiva(uuid);
            when(carregadorAutorizacao.carregar(any(AutorizacaoId.class))).thenReturn(aut);
            when(repository.save(aut)).thenReturn(aut);

            Autorizacao resp = useCaseComValidacaoReal.execute(command);

            assertEquals((int) StatusAutorizacao.ATIVA.getStatusAutorizacao(), resp.getStatus());
            verify(eventPublisher).publishEvent(new AutorizacaoPersistidaEvent(aut));
        }
    }
}
