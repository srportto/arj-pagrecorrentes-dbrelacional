package br.com.srportto.contratocommand.domain.enums;

import br.com.srportto.contratocommand.domain.exception.BusinessException;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public enum TipoProduto {
    PIX_AUTO(1L, true, true),
    DDA_AUTO(2L, true, true);

    private long tipoProduto;
    private boolean habilitadoParaContratar;
    private boolean habilitadoParaCancelar;

    TipoProduto(long tipoProduto, boolean habilitadoParaContratar, boolean habilitadoParaCancelar) {
        this.tipoProduto = tipoProduto;
        this.habilitadoParaContratar = habilitadoParaContratar;
        this.habilitadoParaCancelar = habilitadoParaCancelar;
    }

    public long getTipoProduto() {
        return this.tipoProduto;
    }

    public boolean habilitadoParaContratar() {
        return this.habilitadoParaContratar;
    }

    public boolean habilitadoParaCancelar() {
        return this.habilitadoParaCancelar;
    }

    public static TipoProduto obterTipoProdutoEnumPorId(long tipoProdutoId) {
        for (TipoProduto tipoEnum : TipoProduto.values()) {
            if (tipoEnum.getTipoProduto() == tipoProdutoId) {
                return tipoEnum;
            }
        }
        throw new BusinessException(String.format("tipoProduto %d não conhecido ", tipoProdutoId));
    }

    public static TipoProduto obterTipoProdutoEnumPorNome(String nomeProduto) {
        for (TipoProduto tipoEnum : TipoProduto.values()) {
            if (tipoEnum.name().equalsIgnoreCase(nomeProduto)) {
                return tipoEnum;
            }
        }
        throw new BusinessException(String.format("Produto %s não conhecido ", nomeProduto));
    }
}
