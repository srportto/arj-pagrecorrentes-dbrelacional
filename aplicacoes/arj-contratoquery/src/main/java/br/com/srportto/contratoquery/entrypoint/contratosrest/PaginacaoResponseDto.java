package br.com.srportto.contratoquery.entrypoint.contratosrest;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
