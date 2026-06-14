package br.com.srportto.contratocommand.entrypoint.contratosrest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do AutorizacaoCompletaResponseDto.from")
class AutorizacaoCompletaResponseDtoTest {

    private Autorizacao base(String metadados) {
        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(new IdAutorizacao(UUID.randomUUID(), 10));
        aut.setTipoProduto(TipoProduto.PIX_AUTO);
        aut.setStatus(1);
        aut.setMetadados(metadados);
        return aut;
    }

    @Test
    @DisplayName("from mapeia o id e o metadado JSON")
    void fromComMetadado() {
        Autorizacao aut = base("{\"k\":\"v\"}");
        AutorizacaoCompletaResponseDto dto = AutorizacaoCompletaResponseDto.from(aut);

        assertEquals(aut.getIdAutorizacao().getIdAutorizacao(), dto.getIdAutorizacao());
        assertEquals(TipoProduto.PIX_AUTO, dto.getTipoProduto());
        assertNotNull(dto.getMetadados());
        assertTrue(dto.getMetadados().has("k"));
    }

    @Test
    @DisplayName("from converte metadado nulo/vazio em objeto JSON vazio")
    void fromMetadadoNuloOuVazio() {
        assertTrue(AutorizacaoCompletaResponseDto.from(base(null)).getMetadados().isObject());
        assertTrue(AutorizacaoCompletaResponseDto.from(base("   ")).getMetadados().isObject());
    }

    @Test
    @DisplayName("from tolera metadado com JSON inválido retornando objeto vazio")
    void fromMetadadoInvalido() {
        assertTrue(AutorizacaoCompletaResponseDto.from(base("{quebrado")).getMetadados().isObject());
    }
}
