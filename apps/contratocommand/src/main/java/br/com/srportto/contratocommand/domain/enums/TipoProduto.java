package br.com.srportto.contratocommand.domain.enums;

import br.com.srportto.contratocommand.domain.exception.BusinessException;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@NoArgsConstructor
public enum TipoProduto {
    PIX_AUTO(1L, true, true, new BigDecimal("1000000")),
    DDA_AUTO(2L, true, true, new BigDecimal("250000"));

    private long tipoProduto;
    private boolean habilitadoParaContratar;
    private boolean habilitadoParaCancelar;
    /** Valor máximo de contratação — obrigatório por produto (ver {@code ValorLimiteContrato}). */
    private BigDecimal valorLimiteContratacao;

    TipoProduto(long tipoProduto, boolean habilitadoParaContratar, boolean habilitadoParaCancelar,
                BigDecimal valorLimiteContratacao) {
        this.tipoProduto = tipoProduto;
        this.habilitadoParaContratar = habilitadoParaContratar;
        this.habilitadoParaCancelar = habilitadoParaCancelar;
        this.valorLimiteContratacao = valorLimiteContratacao;
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

    public BigDecimal getValorLimiteContratacao() {
        return this.valorLimiteContratacao;
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
