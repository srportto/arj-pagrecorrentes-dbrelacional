package br.com.srportto.contratocommand.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.srportto.contratocommand.shared.exceptions.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do enum TipoProduto")
class TipoProdutoTest {

    @Test
    @DisplayName("obterTipoProdutoEnumPorId retorna o enum correspondente")
    void obtemPorId() {
        assertEquals(TipoProduto.PIX_AUTO, TipoProduto.obterTipoProdutoEnumPorId(1L));
        assertEquals(TipoProduto.DDA_AUTO, TipoProduto.obterTipoProdutoEnumPorId(2L));
    }

    @Test
    @DisplayName("obterTipoProdutoEnumPorId lança BusinessException para id desconhecido")
    void lancaPorIdDesconhecido() {
        assertThrows(BusinessException.class, () -> TipoProduto.obterTipoProdutoEnumPorId(99L));
    }

    @Test
    @DisplayName("obterTipoProdutoEnumPorNome é case-insensitive")
    void obtemPorNome() {
        assertEquals(TipoProduto.PIX_AUTO, TipoProduto.obterTipoProdutoEnumPorNome("pix_auto"));
        assertEquals(TipoProduto.DDA_AUTO, TipoProduto.obterTipoProdutoEnumPorNome("DDA_AUTO"));
    }

    @Test
    @DisplayName("obterTipoProdutoEnumPorNome lança BusinessException para nome desconhecido")
    void lancaPorNomeDesconhecido() {
        assertThrows(BusinessException.class, () -> TipoProduto.obterTipoProdutoEnumPorNome("CARTAO"));
    }

    @Test
    @DisplayName("getTipoProduto expõe o código")
    void exposeCodigo() {
        assertEquals(1L, TipoProduto.PIX_AUTO.getTipoProduto());
        assertEquals(2L, TipoProduto.DDA_AUTO.getTipoProduto());
    }
}
