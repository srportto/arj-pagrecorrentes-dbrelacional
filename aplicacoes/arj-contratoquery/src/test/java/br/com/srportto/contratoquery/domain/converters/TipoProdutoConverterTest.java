package br.com.srportto.contratoquery.domain.converters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.srportto.contratoquery.domain.enums.TipoProduto;
import br.com.srportto.contratoquery.shared.exceptions.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do TipoProdutoConverter")
class TipoProdutoConverterTest {

    private final TipoProdutoConverter converter = new TipoProdutoConverter();

    @Test
    @DisplayName("convertToDatabaseColumn mapeia enum para código e trata nulo")
    void toDatabaseColumn() {
        assertEquals(1L, converter.convertToDatabaseColumn(TipoProduto.PIX_AUTO));
        assertEquals(2L, converter.convertToDatabaseColumn(TipoProduto.DDA_AUTO));
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    @DisplayName("convertToEntityAttribute mapeia código para enum e trata nulo")
    void toEntityAttribute() {
        assertEquals(TipoProduto.PIX_AUTO, converter.convertToEntityAttribute(1L));
        assertEquals(TipoProduto.DDA_AUTO, converter.convertToEntityAttribute(2L));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    @DisplayName("convertToEntityAttribute lança BusinessException para código inválido")
    void toEntityAttributeInvalido() {
        assertThrows(BusinessException.class, () -> converter.convertToEntityAttribute(99L));
    }
}
