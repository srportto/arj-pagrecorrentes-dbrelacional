package br.com.srportto.contratoquery.domain.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import br.com.srportto.contratoquery.domain.enums.TipoJornadaAutorizacao;

@Converter(autoApply = true)
public class TipoJornadaAutorizacaoConverter implements AttributeConverter<TipoJornadaAutorizacao, Long> {

    @Override
    public Long convertToDatabaseColumn(TipoJornadaAutorizacao tipoJornada) {
        if (tipoJornada == null) {
            return null;
        }
        return tipoJornada.getCodigoJornada();
    }

    @Override
    public TipoJornadaAutorizacao convertToEntityAttribute(Long dbData) {
        if (dbData == null) {
            return null;
        }
        return TipoJornadaAutorizacao.obterJornadaAutorizacaoEnumPorIdJornada(dbData);
    }
}
