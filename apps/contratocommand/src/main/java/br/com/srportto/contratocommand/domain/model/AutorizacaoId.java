package br.com.srportto.contratocommand.domain.model;

import br.com.srportto.contratocommand.domain.exception.BusinessException;

import java.util.UUID;

/**
 * Value object do identificador de autorização. Valida o formato UUID na borda (construtor
 * compacto), evitando que um id malformado atravesse até a camada de aplicação e caia em
 * {@code UUID.fromString} lá dentro — o que hoje gera 500 em vez de 422 (design.md, D1).
 */
public record AutorizacaoId(UUID valor) {

    public AutorizacaoId {
        if (valor == null) {
            throw new BusinessException("Identificador de autorização é obrigatório.");
        }
    }

    /** Fábrica a partir do path variable (String) — onde o formato malformado costuma chegar. */
    public static AutorizacaoId de(String idAutorizacao) {
        if (idAutorizacao == null || idAutorizacao.isBlank()) {
            throw new BusinessException("Identificador de autorização é obrigatório.");
        }
        try {
            return new AutorizacaoId(UUID.fromString(idAutorizacao));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Identificador de autorização inválido: " + idAutorizacao, e);
        }
    }
}
