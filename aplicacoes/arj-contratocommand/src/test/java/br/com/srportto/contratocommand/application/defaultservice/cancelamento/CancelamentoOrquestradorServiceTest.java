package br.com.srportto.contratocommand.application.defaultservice.cancelamento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do CancelamentoOrquestradorService")
class CancelamentoOrquestradorServiceTest {

    @Mock
    private CancelamentoService produtoA;

    @Mock
    private CancelamentoService produtoB;

    @Test
    @DisplayName("seleciona o primeiro produto suportado e delega o cancelamento")
    void selecionaSuportado() {
        CancelarAutorizacaoRequestDto request = TestFixtures.cancelarRequest("id", TipoProduto.PIX_AUTO);
        AutorizacaoCompletaResponseDto dto = AutorizacaoCompletaResponseDto.builder().build();
        when(produtoA.validaCancelamentoSuportado(request)).thenReturn(false);
        when(produtoB.validaCancelamentoSuportado(request)).thenReturn(true);
        when(produtoB.cancelarAutorizacao(request)).thenReturn(dto);

        CancelamentoOrquestradorService orquestrador =
                new CancelamentoOrquestradorService(List.of(produtoA, produtoB));

        assertSame(dto, orquestrador.cancelar(request));
        verify(produtoB).cancelarAutorizacao(request);
    }

    @Test
    @DisplayName("lança BusinessException quando nenhum produto suporta o cancelamento")
    void nenhumSuportado() {
        CancelarAutorizacaoRequestDto request = TestFixtures.cancelarRequest("id", TipoProduto.PIX_AUTO);
        when(produtoA.validaCancelamentoSuportado(request)).thenReturn(false);

        CancelamentoOrquestradorService orquestrador =
                new CancelamentoOrquestradorService(List.of(produtoA));

        assertThrows(BusinessException.class, () -> orquestrador.cancelar(request));
    }
}
