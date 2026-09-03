package br.com.srportto.contratoquery.application.usecase;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.srportto.contratoquery.domain.enums.StatusAutorizacao;
import br.com.srportto.contratoquery.domain.exception.BusinessException;
import br.com.srportto.contratoquery.domain.model.Ordenacao;
import br.com.srportto.contratoquery.domain.model.PaginaAutorizacoes;
import br.com.srportto.contratoquery.domain.port.in.ListarAutorizacoesUseCase;
import br.com.srportto.contratoquery.domain.port.out.AutorizacaoRepository;

/**
 * Caso de uso de listagem paginada. Delega o parse de `ordenarPor` ao value object
 * {@link Ordenacao} e traduz os nomes de status para {@link StatusAutorizacao} — a tradução para
 * caminho JPA/código numérico de banco fica no adaptador (a porta {@link AutorizacaoRepository}
 * não conhece nenhum dos dois). Devolve conteúdo + total (D7): não {@code Page} nem
 * {@code PaginacaoResponseDto}.
 */
@Service
public class ListarAutorizacoesService implements ListarAutorizacoesUseCase {

    private final AutorizacaoRepository repository;

    private static final Integer PAGINA_PADRAO = 0;
    private static final Integer TAMANHO_PADRAO = 20;
    private static final Integer TAMANHO_MAXIMO = 100;

    public ListarAutorizacoesService(AutorizacaoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public ResultadoListagem listar(
            UUID idUnicoContaContratante,
            List<String> statuses,
            Integer pagina,
            Integer tamanho,
            String ordenarPor) {

        if (idUnicoContaContratante == null) {
            throw new BusinessException("idUnicoContaContratante é obrigatório");
        }

        Integer paginaFinal = pagina != null ? pagina : PAGINA_PADRAO;
        Integer tamanhoFinal = tamanho != null ? tamanho : TAMANHO_PADRAO;

        if (paginaFinal < 0) {
            throw new BusinessException("pagina deve ser maior ou igual a 0");
        }

        if (tamanhoFinal <= 0) {
            throw new BusinessException("tamanho deve ser maior que 0");
        }

        if (tamanhoFinal > TAMANHO_MAXIMO) {
            throw new BusinessException(String.format("tamanho não pode ser maior que %d", TAMANHO_MAXIMO));
        }

        Ordenacao ordenacao = (ordenarPor != null && !ordenarPor.isBlank())
                ? Ordenacao.de(ordenarPor)
                : Ordenacao.padrao();

        List<StatusAutorizacao> statusEnums = (statuses == null || statuses.isEmpty())
                ? null
                : converterStatus(statuses);

        PaginaAutorizacoes resultado = repository.listarPorConta(
                idUnicoContaContratante, statusEnums, paginaFinal, tamanhoFinal, ordenacao);

        // tamanhoFinal > 0 sempre aqui — rejeitado acima (linha 59) antes de chegar neste ponto.
        int totalPaginas = (int) Math.ceil((double) resultado.totalElementos() / tamanhoFinal);

        return new ResultadoListagem(
                resultado.conteudo(), paginaFinal, tamanhoFinal, resultado.totalElementos(), totalPaginas);
    }

    private List<StatusAutorizacao> converterStatus(List<String> statuses) {
        return statuses.stream()
                .map(statusStr -> {
                    try {
                        return StatusAutorizacao.valueOf(statusStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new BusinessException(
                                String.format("Status inválido: %s. Use um dos valores: %s",
                                        statusStr, String.join(", ",
                                                java.util.Arrays.stream(StatusAutorizacao.values())
                                                        .map(status -> status.name())
                                                        .collect(Collectors.toList()))));
                    }
                })
                .collect(Collectors.toList());
    }
}
