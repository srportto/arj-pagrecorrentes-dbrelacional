package br.com.srportto.contratocommand.domain.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;

@Converter(autoApply = true)
public class TipoProdutoConverter implements AttributeConverter<TipoProduto, Long> {

    @Override
    public Long convertToDatabaseColumn(TipoProduto tipoProduto) {
        if (tipoProduto == null) {
            return null;
        }
        return tipoProduto.getTipoProduto();
    }

    @Override
    public TipoProduto convertToEntityAttribute(Long dbData) {
        if (dbData == null) {
            return null;
        }
        return TipoProduto.obterTipoProdutoEnumPorId(dbData);
    }
}
