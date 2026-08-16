package br.com.srportto.contratoquery.infrastructure.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.model.Autorizacao;
import br.com.srportto.contratoquery.domain.port.in.ConsultarAutorizacaoUseCase;
import br.com.srportto.contratoquery.domain.port.in.ListarAutorizacoesUseCase;
import br.com.srportto.contratoquery.domain.port.in.ListarAutorizacoesUseCase.ResultadoListagem;
import br.com.srportto.contratoquery.infrastructure.web.contratosrest.AutorizacaoDetalheResponseDto;
import br.com.srportto.contratoquery.infrastructure.web.contratosrest.AutorizacaoResumidaResponseDto;
import br.com.srportto.contratoquery.infrastructure.web.contratosrest.PaginacaoResponseDto;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** O controller agora monta o DTO a partir do modelo de domínio devolvido pelos casos de uso (D6). */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do AutorizacaoController")
class AutorizacaoControllerTest {

    @Mock
    private ListarAutorizacoesUseCase listarAutorizacoesUseCase;

    @Mock
    private ConsultarAutorizacaoUseCase consultarAutorizacaoUseCase;

    private AutorizacaoController controller;

    @BeforeEach
    void setUp() {
        controller = new AutorizacaoController(listarAutorizacoesUseCase, consultarAutorizacaoUseCase);
    }

    @Test
    @DisplayName("listar delega ao caso de uso e monta o envelope de paginação a partir do resultado")
    void listarDelegaEMontaEnvelope() {
        UUID conta = UUID.randomUUID();
        Autorizacao autorizacao = autorizacao();
        ResultadoListagem resultado = new ResultadoListagem(List.of(autorizacao), 0, 20, 1L, 1);
        when(listarAutorizacoesUseCase.listar(eq(conta), any(), eq(0), eq(20), any()))
                .thenReturn(resultado);

        ResponseEntity<PaginacaoResponseDto<AutorizacaoResumidaResponseDto>> resp =
                controller.listar(conta, List.of(), 0, 20, "dataHoraInclusao,desc");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().getConteudo().size());
        assertEquals(0, resp.getBody().getPaginaAtual());
        assertEquals(1, resp.getBody().getTotalPaginas());
        assertEquals(1L, resp.getBody().getTotalElementos());
        assertEquals(20, resp.getBody().getTamanho());
        verify(listarAutorizacoesUseCase).listar(eq(conta), any(), eq(0), eq(20), any());
    }

    @Test
    @DisplayName("consultarPorId delega ao caso de uso e monta o DTO a partir do modelo de domínio")
    void consultarPorIdDelegaEMontaDto() {
        UUID id = UUID.randomUUID();
        Autorizacao autorizacao = autorizacao();
        when(consultarAutorizacaoUseCase.consultarPorId(id)).thenReturn(autorizacao);

        ResponseEntity<AutorizacaoDetalheResponseDto> resp = controller.consultarPorId(id);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(autorizacao.getIdAutorizacao(), resp.getBody().getIdAutorizacao());
        verify(consultarAutorizacaoUseCase).consultarPorId(id);
    }

    private Autorizacao autorizacao() {
        return Autorizacao.builder()
                .idAutorizacao(UUID.randomUUID())
                .status(4)
                .motivoStatus("Teste")
                .dataInicioVigencia(LocalDate.now())
                .dataFimVigencia(LocalDate.now().plusDays(30))
                .dataHoraInclusao(LocalDateTime.now())
                .dataHoraUltimaAtualizacao(LocalDateTime.now())
                .valorAutorizacao(BigDecimal.TEN)
                .valorLimite(BigDecimal.TEN)
                .idUnicoContaContratante(UUID.randomUUID())
                .idPessoaPagadora(UUID.randomUUID())
                .idPessoaDevedora(UUID.randomUUID())
                .idPessoaRecebedora(UUID.randomUUID())
                .idAutorizacaoEmpresa("EMP1")
                .metadados("{}")
                .build();
    }
}
