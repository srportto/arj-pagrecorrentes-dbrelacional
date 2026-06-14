package br.com.srportto.contratoquery.entrypoint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import br.com.srportto.contratoquery.application.autorizacao.ConsultarAutorizacaoService;
import br.com.srportto.contratoquery.application.autorizacao.ListarAutorizacoesService;
import br.com.srportto.contratoquery.entrypoint.contratosrest.AutorizacaoDetalheResponseDto;
import br.com.srportto.contratoquery.entrypoint.contratosrest.AutorizacaoResumidaResponseDto;
import br.com.srportto.contratoquery.entrypoint.contratosrest.PaginacaoResponseDto;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do AutorizacaoController")
class AutorizacaoControllerTest {

    @Mock
    private ListarAutorizacoesService listarAutorizacoesService;

    @Mock
    private ConsultarAutorizacaoService consultarAutorizacaoService;

    @InjectMocks
    private AutorizacaoController controller;

    @Test
    @DisplayName("listar delega ao service e responde 200 com o corpo")
    void listarDelegaERetornaOk() {
        UUID conta = UUID.randomUUID();
        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> esperado =
                PaginacaoResponseDto.<AutorizacaoResumidaResponseDto>builder().build();
        when(listarAutorizacoesService.listar(eq(conta), any(), eq(0), eq(20), any()))
                .thenReturn(esperado);

        ResponseEntity<PaginacaoResponseDto<AutorizacaoResumidaResponseDto>> resp =
                controller.listar(conta, List.of(), 0, 20, "dataHoraInclusao,desc");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(esperado, resp.getBody());
        verify(listarAutorizacoesService).listar(eq(conta), any(), eq(0), eq(20), any());
    }

    @Test
    @DisplayName("consultarPorId delega ao service e responde 200 com o corpo")
    void consultarPorIdDelegaERetornaOk() {
        UUID id = UUID.randomUUID();
        AutorizacaoDetalheResponseDto esperado = AutorizacaoDetalheResponseDto.builder().build();
        when(consultarAutorizacaoService.consultarPorId(id)).thenReturn(esperado);

        ResponseEntity<AutorizacaoDetalheResponseDto> resp = controller.consultarPorId(id);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(esperado, resp.getBody());
        verify(consultarAutorizacaoService).consultarPorId(id);
    }
}
