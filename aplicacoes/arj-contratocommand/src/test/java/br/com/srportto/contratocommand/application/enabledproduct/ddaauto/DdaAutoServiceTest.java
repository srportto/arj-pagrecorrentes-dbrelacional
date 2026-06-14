package br.com.srportto.contratocommand.application.enabledproduct.ddaauto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.application.enabledproduct.ddaauto.usecases.CancelarDdaAutoUseCase;
import br.com.srportto.contratocommand.application.enabledproduct.ddaauto.usecases.CriarDdaAutoUseCase;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do DdaAutoService")
class DdaAutoServiceTest {

    @Mock
    private CriarDdaAutoUseCase criarDdaAutoUseCase;
    @Mock
    private CancelarDdaAutoUseCase cancelarDdaAutoUseCase;

    @InjectMocks
    private DdaAutoService service;

    @Test
    @DisplayName("validaContratacaoSuportada: true só para DDA_AUTO")
    void validaContratacao() {
        assertTrue(service.validaContratacaoSuportada(TestFixtures.criarRequestDda()));
        assertFalse(service.validaContratacaoSuportada(TestFixtures.criarRequestPix()));
        assertFalse(service.validaContratacaoSuportada(TestFixtures.criarRequest(
                null, BigDecimal.ONE, LocalDate.now().plusDays(1), null)));
    }

    @Test
    @DisplayName("criarAutorizacao delega ao CriarDdaAutoUseCase")
    void criarDelega() {
        CriarAutorizacaoRequest request = TestFixtures.criarRequestDda();
        AutorizacaoCompletaResponseDto dto = AutorizacaoCompletaResponseDto.builder().build();
        when(criarDdaAutoUseCase.execute(request)).thenReturn(dto);

        assertSame(dto, service.criarAutorizacao(request));
    }

    @Test
    @DisplayName("validaCancelamentoSuportado: true só para DDA_AUTO")
    void validaCancelamento() {
        assertTrue(service.validaCancelamentoSuportado(TestFixtures.cancelarRequest("id", TipoProduto.DDA_AUTO)));
        assertFalse(service.validaCancelamentoSuportado(TestFixtures.cancelarRequest("id", TipoProduto.PIX_AUTO)));
    }

    @Test
    @DisplayName("cancelarAutorizacao delega ao CancelarDdaAutoUseCase")
    void cancelarDelega() {
        CancelarAutorizacaoRequestDto request = TestFixtures.cancelarRequest("id", TipoProduto.DDA_AUTO);
        AutorizacaoCompletaResponseDto dto = AutorizacaoCompletaResponseDto.builder().build();
        when(cancelarDdaAutoUseCase.execute(request)).thenReturn(dto);

        assertSame(dto, service.cancelarAutorizacao(request));
    }
}
