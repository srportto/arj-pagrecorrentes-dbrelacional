package br.com.srportto.contratoquery.domain.enums;

import br.com.srportto.contratoquery.domain.exception.BusinessException;

/**
 * Direção de ordenação da listagem — vocabulário de domínio, sem dependência de
 * {@code org.springframework.data.domain.Sort.Direction} (D2 de {@code Ordenacao}).
 */
public enum DirecaoOrdenacao {
    ASC,
    DESC;

    /** Aceita "asc"/"desc" em qualquer caixa, com trim. Qualquer outro valor lança {@link BusinessException}. */
    public static DirecaoOrdenacao porNome(String nome) {
        String normalizado = nome.trim();
        return switch (normalizado.toLowerCase()) {
            case "asc" -> ASC;
            case "desc" -> DESC;
            default -> throw new BusinessException(
                    String.format("Direção de ordenação inválida: %s. Direções aceitas: asc, desc", nome));
        };
    }
}
