package br.com.srportto.contratocommand.entrypoint;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.defaultservice.cancelamento.CancelamentoOrquestradorService;
import br.com.srportto.contratocommand.application.defaultservice.contratacao.ContratacaoOrquestradorService;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do AutorizacaoController")
class AutorizacaoControllerTest {

    @Mock
    private ContratacaoOrquestradorService orquestradorContratacaoService;
    @Mock
    private CancelamentoOrquestradorService orquestradorCancelamentoService;

    @InjectMocks
    private AutorizacaoController controller;

    @AfterEach
    void limparContexto() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("insert delega ao orquestrador e responde 201 com Location")
    void insertRetornaCreated() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        CriarAutorizacaoRequest request = TestFixtures.criarRequestPix();
        AutorizacaoCompletaResponseDto dto = AutorizacaoCompletaResponseDto.builder()
                .idAutorizacao(UUID.randomUUID())
                .build();
        when(orquestradorContratacaoService.criar(request)).thenReturn(dto);

        ResponseEntity<AutorizacaoCompletaResponseDto> resp = controller.insert(request);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertSame(dto, resp.getBody());
    }

    @Test
    @DisplayName("cancelar resolve o produto pelo header e responde 200")
    void cancelarRetornaOk() {
        CancelarAutorizacaoRequestDto request = CancelarAutorizacaoRequestDto.builder()
                .codigoCanalCancelamento("C1")
                .idPessoaCancelamento(UUID.randomUUID())
                .motivoCancelamento("teste")
                .build();
        AutorizacaoCompletaResponseDto dto = AutorizacaoCompletaResponseDto.builder().build();
        when(orquestradorCancelamentoService.cancelar(any())).thenReturn(dto);

        ResponseEntity<AutorizacaoCompletaResponseDto> resp =
                controller.cancelar("550e8400-e29b-41d4-a716-446655440000", "PIX_AUTO", request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(dto, resp.getBody());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", request.getIdAutorizacao());
    }
}
