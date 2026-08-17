package br.com.srportto.contratoquery.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import br.com.srportto.contratoquery.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratoquery.domain.exception.ApplicationException;

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
        // Mesmo raciocínio do TipoProdutoConverter: código não mapeável na leitura é dado
        // corrompido no banco, não requisição inválida do cliente — 500, não 422.
        try {
            return TipoJornadaAutorizacao.obterJornadaAutorizacaoEnumPorIdJornada(dbData);
        } catch (RuntimeException e) {
            throw new ApplicationException("Código de tipoJornada inválido na linha lida do banco: " + dbData, e);
        }
    }
}
