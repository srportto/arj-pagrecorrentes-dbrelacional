package br.com.srportto.contratocommand.infrastructure.web;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.model.Autorizacao;
import br.com.srportto.contratocommand.domain.port.in.CancelarAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.port.in.CancelarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.port.in.DecidirAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.port.in.DecidirAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.infrastructure.web.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.infrastructure.web.contratosrest.CancelarAutorizacaoRequest;
import br.com.srportto.contratocommand.infrastructure.web.contratosrest.CriarAutorizacaoRequest;
import br.com.srportto.contratocommand.infrastructure.web.contratosrest.DecisaoAutorizacaoRequest;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do AutorizacaoController")
class AutorizacaoControllerTest {

    @Mock
    private CriarAutorizacaoUseCase criarAutorizacaoUseCase;
    @Mock
    private CancelarAutorizacaoUseCase cancelarAutorizacaoUseCase;
    @Mock
    private DecidirAutorizacaoUseCase decidirAutorizacaoUseCase;

    @InjectMocks
    private AutorizacaoController controller;

    @AfterEach
    void limparContexto() {
        RequestContextHolder.resetRequestAttributes();
    }

    private Autorizacao autorizacaoComId(UUID id) {
        var aut = new Autorizacao();
        aut.setIdAutorizacao(id);
        aut.setIdParticaoConta(5);
        return aut;
    }

    @Test
    @DisplayName("insert delega ao CriarAutorizacaoUseCase e responde 201 com Location")
    void insertRetornaCreated() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        CriarAutorizacaoRequest request = TestFixtures.criarRequestPix();
        Autorizacao autorizada = autorizacaoComId(UUID.randomUUID());
        when(criarAutorizacaoUseCase.execute(any(CriarAutorizacaoCommand.class))).thenReturn(autorizada);

        ResponseEntity<AutorizacaoCompletaResponseDto> resp = controller.insert(request, "SPI_J1");

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(autorizada.getIdAutorizacao(), resp.getBody().getIdAutorizacao());

        ArgumentCaptor<CriarAutorizacaoCommand> captor = ArgumentCaptor.forClass(CriarAutorizacaoCommand.class);
        verify(criarAutorizacaoUseCase).execute(captor.capture());
        CriarAutorizacaoCommand command = captor.getValue();
        assertEquals(TipoJornadaAutorizacao.SPI_J1, command.tipoJornada());
        assertEquals(TipoProduto.PIX_AUTO, command.tipoProduto());
        assertEquals(request.valor(), command.valor());
        assertEquals(request.idAutorizacaoEmpresa(), command.idAutorizacaoEmpresa());
        assertEquals(request.idUnicoContaContratante(), command.idUnicoContaContratante());
    }

    @Test
    @DisplayName("insert com tipoProduto desconhecido no body lança BusinessException antes de chamar o use case")
    void insertComTipoProdutoDesconhecidoLancaAntesDoUseCase() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        CriarAutorizacaoRequest request = TestFixtures.criarRequest(
                "CARTAO_CREDITO", java.math.BigDecimal.ONE, java.time.LocalDate.now().plusDays(1), null);

        assertThrows(BusinessException.class, () -> controller.insert(request, "SPI_J1"));

        verifyNoInteractions(criarAutorizacaoUseCase);
    }

    @Test
    @DisplayName("cancelar resolve o produto pelo header, monta o comando e responde 200")
    void cancelarRetornaOk() {
        CancelarAutorizacaoRequest dados = new CancelarAutorizacaoRequest("C1", UUID.randomUUID(), "teste");
        Autorizacao autorizada = autorizacaoComId(UUID.randomUUID());
        when(cancelarAutorizacaoUseCase.execute(any())).thenReturn(autorizada);

        ResponseEntity<AutorizacaoCompletaResponseDto> resp =
                controller.cancelar("550e8400-e29b-41d4-a716-446655440000", "PIX_AUTO", dados);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(autorizada.getIdAutorizacao(), resp.getBody().getIdAutorizacao());

        ArgumentCaptor<CancelarAutorizacaoCommand> captor = ArgumentCaptor.forClass(CancelarAutorizacaoCommand.class);
        verify(cancelarAutorizacaoUseCase).execute(captor.capture());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", captor.getValue().idAutorizacao());
        assertEquals(TipoProduto.PIX_AUTO, captor.getValue().tipoProduto());
        assertEquals(dados.codigoCanalCancelamento(), captor.getValue().codigoCanalCancelamento());
        assertEquals(dados.idPessoaCancelamento(), captor.getValue().idPessoaCancelamento());
        assertEquals(dados.motivoCancelamento(), captor.getValue().motivoCancelamento());
    }

    @Test
    @DisplayName("cancelar com header tipoProduto desconhecido lança BusinessException antes de chamar o use case")
    void cancelarComTipoProdutoDesconhecidoLancaAntesDoUseCase() {
        CancelarAutorizacaoRequest dados = new CancelarAutorizacaoRequest("C1", UUID.randomUUID(), "teste");

        assertThrows(BusinessException.class,
                () -> controller.cancelar("550e8400-e29b-41d4-a716-446655440000", "CARTAO_CREDITO", dados));

        verifyNoInteractions(cancelarAutorizacaoUseCase);
    }

    @Test
    @DisplayName("decidir resolve o produto pelo header, monta o comando e responde 200")
    void decidirRetornaOk() {
        DecisaoAutorizacaoRequest dados = new DecisaoAutorizacaoRequest("APROVAR", "C1", UUID.randomUUID());
        Autorizacao autorizada = autorizacaoComId(UUID.randomUUID());
        when(decidirAutorizacaoUseCase.execute(any())).thenReturn(autorizada);

        ResponseEntity<AutorizacaoCompletaResponseDto> resp =
                controller.decidir("550e8400-e29b-41d4-a716-446655440000", "PIX_AUTO", dados);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(autorizada.getIdAutorizacao(), resp.getBody().getIdAutorizacao());

        ArgumentCaptor<DecidirAutorizacaoCommand> captor = ArgumentCaptor.forClass(DecidirAutorizacaoCommand.class);
        verify(decidirAutorizacaoUseCase).execute(captor.capture());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", captor.getValue().idAutorizacao());
        assertEquals(TipoProduto.PIX_AUTO, captor.getValue().tipoProduto());
        assertEquals(dados.acao(), captor.getValue().acao());
        assertEquals(dados.codigoCanalDecisao(), captor.getValue().codigoCanalDecisao());
        assertEquals(dados.idPessoaDecisao(), captor.getValue().idPessoaDecisao());
    }

    @Test
    @DisplayName("decidir com header tipoProduto desconhecido lança BusinessException antes de chamar o use case")
    void decidirComTipoProdutoDesconhecidoLancaAntesDoUseCase() {
        DecisaoAutorizacaoRequest dados = new DecisaoAutorizacaoRequest("APROVAR", "C1", UUID.randomUUID());

        assertThrows(BusinessException.class,
                () -> controller.decidir("550e8400-e29b-41d4-a716-446655440000", "CARTAO_CREDITO", dados));

        verifyNoInteractions(decidirAutorizacaoUseCase);
    }

    // "acao" invalida/ausente vira 422 dentro do use case (AcaoDecisaoValida / @NotNull), não aqui
    // — ver DecidirAutorizacaoServiceTest.ComValidacaoReal.
}
