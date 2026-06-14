package br.com.srportto.contratocommand.application.defaultservice.contratacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do ContratacaoOrquestradorService")
class ContratacaoOrquestradorServiceTest {

    @Mock
    private ContratacaoService produtoA;

    @Mock
    private ContratacaoService produtoB;

    @Test
    @DisplayName("seleciona o primeiro produto suportado e delega a criação")
    void selecionaSuportado() {
        CriarAutorizacaoRequest request = TestFixtures.criarRequestPix();
        AutorizacaoCompletaResponseDto dto = AutorizacaoCompletaResponseDto.builder().build();
        when(produtoA.validaContratacaoSuportada(request)).thenReturn(false);
        when(produtoB.validaContratacaoSuportada(request)).thenReturn(true);
        when(produtoB.criarAutorizacao(request)).thenReturn(dto);

        ContratacaoOrquestradorService orquestrador =
                new ContratacaoOrquestradorService(List.of(produtoA, produtoB));

        assertSame(dto, orquestrador.criar(request));
        verify(produtoB).criarAutorizacao(request);
    }

    @Test
    @DisplayName("lança BusinessException quando nenhum produto suporta a requisição")
    void nenhumSuportado() {
        CriarAutorizacaoRequest request = TestFixtures.criarRequestPix();
        when(produtoA.validaContratacaoSuportada(request)).thenReturn(false);

        ContratacaoOrquestradorService orquestrador =
                new ContratacaoOrquestradorService(List.of(produtoA));

        assertThrows(BusinessException.class, () -> orquestrador.criar(request));
    }
}
