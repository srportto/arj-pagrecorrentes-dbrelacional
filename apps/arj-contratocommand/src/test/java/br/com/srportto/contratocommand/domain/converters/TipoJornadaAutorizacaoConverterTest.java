package br.com.srportto.contratocommand.domain.converters;

import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do TipoJornadaAutorizacaoConverter")
class TipoJornadaAutorizacaoConverterTest {

    private final TipoJornadaAutorizacaoConverter converter = new TipoJornadaAutorizacaoConverter();

    @Test
    @DisplayName("convertToDatabaseColumn mapeia enum para código e trata nulo")
    void toDatabaseColumn() {
        assertEquals(1L, converter.convertToDatabaseColumn(TipoJornadaAutorizacao.SPI_J1));
        assertEquals(0L, converter.convertToDatabaseColumn(TipoJornadaAutorizacao.DESCONHECIDA));
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    @DisplayName("convertToEntityAttribute mapeia código para enum, incluindo o default 0 de linhas legadas, e trata nulo")
    void toEntityAttribute() {
        assertEquals(TipoJornadaAutorizacao.SPI_J1, converter.convertToEntityAttribute(1L));
        assertEquals(TipoJornadaAutorizacao.DESCONHECIDA, converter.convertToEntityAttribute(0L));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    @DisplayName("convertToEntityAttribute lança BusinessException para código inválido")
    void toEntityAttributeInvalido() {
        assertThrows(BusinessException.class, () -> converter.convertToEntityAttribute(99L));
    }
}
