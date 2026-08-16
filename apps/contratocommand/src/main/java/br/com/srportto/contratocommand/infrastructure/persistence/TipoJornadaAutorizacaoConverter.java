package br.com.srportto.contratocommand.infrastructure.persistence;

import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

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
