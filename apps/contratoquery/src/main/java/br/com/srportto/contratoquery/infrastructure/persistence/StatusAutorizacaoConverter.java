package br.com.srportto.contratoquery.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import br.com.srportto.contratoquery.domain.enums.StatusAutorizacao;
import br.com.srportto.contratoquery.domain.exception.ApplicationException;

/** Mesmo padrão de {@link TipoProdutoConverter}/{@link TipoJornadaAutorizacaoConverter} — status deixa de ser traduzido tarde, na borda web, duplicado em dois DTOs. */
@Converter(autoApply = true)
public class StatusAutorizacaoConverter implements AttributeConverter<StatusAutorizacao, Integer> {

    @Override
    public Integer convertToDatabaseColumn(StatusAutorizacao status) {
        if (status == null) {
            return null;
        }
        return (int) status.getStatusAutorizacao();
    }

    @Override
    public StatusAutorizacao convertToEntityAttribute(Integer dbData) {
        if (dbData == null) {
            return null;
        }
        // Código não mapeável na leitura é dado corrompido no banco, não requisição inválida do
        // cliente — 500, não 422.
        try {
            return StatusAutorizacao.obterStatusEnumPorIdStatus(dbData);
        } catch (RuntimeException e) {
            throw new ApplicationException("Código de status inválido na linha lida do banco: " + dbData, e);
        }
    }
}
