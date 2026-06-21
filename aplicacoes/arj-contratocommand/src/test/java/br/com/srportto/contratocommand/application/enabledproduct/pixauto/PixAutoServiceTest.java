package br.com.srportto.contratocommand.application.enabledproduct.pixauto;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.autorizacao.usecases.CancelarAutorizacaoUseCase;
import br.com.srportto.contratocommand.application.autorizacao.usecases.CriarAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do PixAutoService")
class PixAutoServiceTest {

    @Mock
    private CriarAutorizacaoUseCase criarAutorizacaoUseCase;
    @Mock
    private CancelarAutorizacaoUseCase cancelarAutorizacaoUseCase;

    @InjectMocks
    private PixAutoService service;

    @Test
    @DisplayName("validaContratacaoSuportada: true só para PIX_AUTO")
    void validaContratacao() {
        assertTrue(service.validaContratacaoSuportada(TestFixtures.criarRequestPix()));
        assertFalse(service.validaContratacaoSuportada(TestFixtures.criarRequestDda()));
        assertFalse(service.validaContratacaoSuportada(TestFixtures.criarRequest(
                null, BigDecimal.ONE, LocalDate.now().plusDays(1), null, TipoJornadaAutorizacao.SPI_J1)));
    }

    @Test
    @DisplayName("criarAutorizacao delega ao CriarAutorizacaoUseCase")
    void criarDelega() {
        CriarAutorizacaoRequest request = TestFixtures.criarRequestPix();
        AutorizacaoCompletaResponseDto dto = AutorizacaoCompletaResponseDto.builder().build();
        when(criarAutorizacaoUseCase.execute(request)).thenReturn(dto);

        assertSame(dto, service.criarAutorizacao(request));
    }

    @Test
    @DisplayName("validaCancelamentoSuportado: true só para PIX_AUTO")
    void validaCancelamento() {
        assertTrue(service.validaCancelamentoSuportado(TestFixtures.cancelarRequest("id", TipoProduto.PIX_AUTO)));
        assertFalse(service.validaCancelamentoSuportado(TestFixtures.cancelarRequest("id", TipoProduto.DDA_AUTO)));
    }

    @Test
    @DisplayName("cancelarAutorizacao delega ao CancelarAutorizacaoUseCase")
    void cancelarDelega() {
        CancelarAutorizacaoRequestDto request = TestFixtures.cancelarRequest("id", TipoProduto.PIX_AUTO);
        AutorizacaoCompletaResponseDto dto = AutorizacaoCompletaResponseDto.builder().build();
        when(cancelarAutorizacaoUseCase.execute(request)).thenReturn(dto);

        assertSame(dto, service.cancelarAutorizacao(request));
    }
}
