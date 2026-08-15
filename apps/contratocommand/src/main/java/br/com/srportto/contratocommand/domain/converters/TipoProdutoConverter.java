package br.com.srportto.contratocommand.domain.converters;

import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

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
