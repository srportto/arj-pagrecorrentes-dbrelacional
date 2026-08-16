package br.com.srportto.contratoquery.application.usecase;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.srportto.contratoquery.domain.enums.StatusAutorizacao;
import br.com.srportto.contratoquery.domain.exception.BusinessException;
import br.com.srportto.contratoquery.domain.model.PaginaAutorizacoes;
import br.com.srportto.contratoquery.domain.port.in.ListarAutorizacoesUseCase;
import br.com.srportto.contratoquery.domain.port.out.AutorizacaoRepository;

/**
 * Caso de uso de listagem paginada. A tradução do campo de ordenação para o caminho JPA (ex.:
 * {@code idAutorizacao.idAutorizacao}, por causa da chave composta) é feita aqui porque é a mesma
 * whitelist de sempre — só o formato de saída da porta mudou (D7): conteúdo + total, não
 * {@code Page} nem {@code PaginacaoResponseDto}.
 */
@Service
public class ListarAutorizacoesService implements ListarAutorizacoesUseCase {

    private final AutorizacaoRepository repository;

    private static final Integer PAGINA_PADRAO = 0;
    private static final Integer TAMANHO_PADRAO = 20;
    private static final Integer TAMANHO_MAXIMO = 100;
    private static final String CAMPO_ORDENACAO_PADRAO = "dataHoraInclusao";
    private static final boolean ASCENDENTE_PADRAO = false;

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

        String campoOrdenacaoJpa = CAMPO_ORDENACAO_PADRAO;
        boolean ascendente = ASCENDENTE_PADRAO;

        if (ordenarPor != null && !ordenarPor.isBlank()) {
            String[] partes = ordenarPor.split(",");
            if (partes.length >= 1) {
                campoOrdenacaoJpa = mapearCampoDTO(partes[0].trim());
            }
            if (partes.length >= 2) {
                ascendente = "asc".equalsIgnoreCase(partes[1].trim());
            }
        }

        List<Integer> statusCodigos = (statuses == null || statuses.isEmpty())
                ? null
                : converterStatusParaInteiros(statuses);

        PaginaAutorizacoes resultado = repository.listarPorConta(
                idUnicoContaContratante, statusCodigos, paginaFinal, tamanhoFinal, campoOrdenacaoJpa, ascendente);

        int totalPaginas = tamanhoFinal == 0
                ? 1
                : (int) Math.ceil((double) resultado.totalElementos() / tamanhoFinal);

        return new ResultadoListagem(
                resultado.conteudo(), paginaFinal, tamanhoFinal, resultado.totalElementos(), totalPaginas);
    }

    private String mapearCampoDTO(String campoDtoOuEntidade) {
        String mapeado = switch (campoDtoOuEntidade) {
            case "dataCriacao" -> "dataHoraInclusao";
            case "valor" -> "valorAutorizacao";
            case "idAutorizacao" -> "idAutorizacao.idAutorizacao";
            case "dataInicioVigencia" -> "dataInicioVigencia";
            case "dataFimVigencia" -> "dataFimVigencia";
            case "idPessoaRecebedora" -> "idPessoaRecebedora";
            default -> null;
        };

        if (mapeado != null) {
            return mapeado;
        }

        // Whitelist: só passam campos de entidade explicitamente permitidos
        if (campoDtoOuEntidade.equals("dataHoraInclusao") ||
            campoDtoOuEntidade.equals("status") ||
            campoDtoOuEntidade.equals("valorAutorizacao") ||
            campoDtoOuEntidade.equals("dataInicioVigencia") ||
            campoDtoOuEntidade.equals("dataFimVigencia") ||
            campoDtoOuEntidade.equals("idPessoaRecebedora")) {
            return campoDtoOuEntidade;
        }

        throw new BusinessException(
                String.format("Campo de ordenação inválido: %s. Campos aceitos: " +
                        "dataCriacao, dataHoraInclusao, valor, valorAutorizacao, idAutorizacao, " +
                        "dataInicioVigencia, dataFimVigencia, idPessoaRecebedora, status",
                        campoDtoOuEntidade));
    }

    private List<Integer> converterStatusParaInteiros(List<String> statuses) {
        return statuses.stream()
                .map(statusStr -> {
                    try {
                        StatusAutorizacao statusEnum = StatusAutorizacao.valueOf(statusStr.toUpperCase());
                        return (int) statusEnum.getStatusAutorizacao();
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
