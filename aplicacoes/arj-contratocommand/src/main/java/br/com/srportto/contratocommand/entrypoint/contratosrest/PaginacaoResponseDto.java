package br.com.srportto.contratocommand.entrypoint.contratosrest;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO genérico para resposta paginada.
 * Encapsula uma lista de elementos com metadados de paginação.
 *
 * @param <T> tipo do elemento contido na lista
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaginacaoResponseDto<T> {

    private List<T> conteudo;
    private Integer paginaAtual;
    private Integer totalPaginas;
    private Long totalElementos;
    private Integer tamanho;
}
