package br.com.srportto.contratoquery.domain.enums;

import br.com.srportto.contratoquery.shared.exceptions.BusinessException;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public enum TipoProduto {
    PIX_AUTO(1L),
    DDA_AUTO(2L);

    private long tipoProduto;

    TipoProduto(long tipoProduto) {
        this.tipoProduto = tipoProduto;
    }

    public long getTipoProduto() {
        return this.tipoProduto;
    }

    public static TipoProduto obterTipoProdutoEnumPorId(long tipoProdutoId) {
        for (TipoProduto tipoEnum : TipoProduto.values()) {
            if (tipoEnum.getTipoProduto() == tipoProdutoId) {
                return tipoEnum;
            }
        }
        throw new BusinessException(String.format("tipoProduto %d não conhecido ", tipoProdutoId));
    }
}
