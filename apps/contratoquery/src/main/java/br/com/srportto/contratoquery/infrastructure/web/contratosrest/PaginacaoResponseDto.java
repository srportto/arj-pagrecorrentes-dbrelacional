package br.com.srportto.contratoquery.infrastructure.web.contratosrest;

import java.util.List;

public record PaginacaoResponseDto<T>(
        List<T> conteudo,
        Integer paginaAtual,
        Integer totalPaginas,
        Long totalElementos,
        Integer tamanho) {
}
