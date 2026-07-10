package br.com.srportto.contratocommand.entrypoint;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.cancelamento.CancelarAutorizacaoUseCase;
import br.com.srportto.contratocommand.application.contratacao.CriarAutorizacaoUseCase;
import br.com.srportto.contratocommand.application.cancelamento.CancelamentoContext;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequest;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
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
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @InjectMocks
    private AutorizacaoController controller;

    @AfterEach
    void limparContexto() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("insert delega ao CriarAutorizacaoUseCase e responde 201 com Location")
    void insertRetornaCreated() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        CriarAutorizacaoRequest request = TestFixtures.criarRequestPix();
        AutorizacaoCompletaResponseDto dto = AutorizacaoCompletaResponseDto.builder()
                .idAutorizacao(UUID.randomUUID())
                .build();
        when(criarAutorizacaoUseCase.execute(any(CriarAutorizacaoRequest.class))).thenReturn(dto);

        ResponseEntity<AutorizacaoCompletaResponseDto> resp = controller.insert(request, "SPI_J1");

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertSame(dto, resp.getBody());
        verify(criarAutorizacaoUseCase).execute(any(CriarAutorizacaoRequest.class));
    }

    @Test
    @DisplayName("cancelar resolve o produto pelo header, monta o contexto e responde 200")
    void cancelarRetornaOk() {
        CancelarAutorizacaoRequest dados = new CancelarAutorizacaoRequest("C1", UUID.randomUUID(), "teste");
        AutorizacaoCompletaResponseDto dto = AutorizacaoCompletaResponseDto.builder().build();
        when(cancelarAutorizacaoUseCase.execute(any())).thenReturn(dto);

        ResponseEntity<AutorizacaoCompletaResponseDto> resp =
                controller.cancelar("550e8400-e29b-41d4-a716-446655440000", "PIX_AUTO", dados);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(dto, resp.getBody());

        ArgumentCaptor<CancelamentoContext> captor = ArgumentCaptor.forClass(CancelamentoContext.class);
        verify(cancelarAutorizacaoUseCase).execute(captor.capture());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", captor.getValue().idAutorizacao());
        assertEquals(TipoProduto.PIX_AUTO, captor.getValue().tipoProduto());
        assertSame(dados, captor.getValue().dados());
    }

    @Test
    @DisplayName("cancelar com header tipoProduto desconhecido lança BusinessException antes de chamar o use case")
    void cancelarComTipoProdutoDesconhecidoLancaAntesDoUseCase() {
        CancelarAutorizacaoRequest dados = new CancelarAutorizacaoRequest("C1", UUID.randomUUID(), "teste");

        assertThrows(BusinessException.class,
                () -> controller.cancelar("550e8400-e29b-41d4-a716-446655440000", "CARTAO_CREDITO", dados));

        verifyNoInteractions(cancelarAutorizacaoUseCase);
    }
}
