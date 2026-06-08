package br.com.srportto.contratocommand.application.enabledproduct.pixauto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoResumidaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.PaginacaoResponseDto;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import lombok.AllArgsConstructor;

/**
 * Serviço de listagem de autorizações com filtros, ordenação e paginação.
 * Responsável por orquestrar a busca de autorizações no repositório
 * e converter os resultados para DTOs de resposta reduzida.
 */
@Service
@AllArgsConstructor
public class ListarAutorizacoesService {

    private final PixAutoRepository pixAutoRepository;

    private static final Integer PAGINA_PADRAO = 0;
    private static final Integer TAMANHO_PADRAO = 20;
    private static final String CAMPO_ORDENACAO_PADRAO = "dataHoraInclusao";
    private static final Sort.Direction DIRECAO_PADRAO = Sort.Direction.DESC;

    /**
     * Lista autorizações de uma conta com filtros opcionais de status,
     * ordenação e paginação.
     *
     * @param idUnicoContaContratante UUID da conta (obrigatório)
     * @param statuses lista de status desejados (opcional)
     * @param pagina número da página (opcional, padrão: 0)
     * @param tamanho quantidade de itens por página (opcional, padrão: 20)
     * @param ordenarPor campo e direção de ordenação em formato "campo,direcao" (opcional)
     * @return DTO paginado com autorizações resumidas
     * @throws BusinessException se idUnicoContaContratante for nulo ou inválido
     */
    public PaginacaoResponseDto<AutorizacaoResumidaResponseDto> listar(
            UUID idUnicoContaContratante,
            List<String> statuses,
            Integer pagina,
            Integer tamanho,
            String ordenarPor) {

        // Validar idUnicoContaContratante (obrigatório)
        if (idUnicoContaContratante == null) {
            throw new BusinessException("idUnicoContaContratante é obrigatório");
        }

        // Aplicar padrões para paginação se não informados
        Integer paginaFinal = pagina != null ? pagina : PAGINA_PADRAO;
        Integer tamanhoFinal = tamanho != null ? tamanho : TAMANHO_PADRAO;

        // Construir objeto Pageable com ordenação
        Pageable pageable = construirPageable(paginaFinal, tamanhoFinal, ordenarPor);

        // Buscar autorizações (com ou sem filtro de status)
        Page<Autorizacao> pageAutorizacoes;
        if (statuses == null || statuses.isEmpty()) {
            // Nenhum status filtrado, retornar todas
            pageAutorizacoes = pixAutoRepository.findByIdUnicoContaContratante(
                    idUnicoContaContratante,
                    pageable);
        } else {
            // Converter status de String para Integer
            List<Integer> statusInteiros = converterStatusParaInteiros(statuses);
            pageAutorizacoes = pixAutoRepository.findByIdUnicoContaContratanteAndStatusIn(
                    idUnicoContaContratante,
                    statusInteiros,
                    pageable);
        }

        // Converter para DTOs resumidos
        List<AutorizacaoResumidaResponseDto> conteudo = pageAutorizacoes.getContent()
                .stream()
                .map(AutorizacaoResumidaResponseDto::from)
                .collect(Collectors.toList());

        // Construir resposta paginada
        return PaginacaoResponseDto.<AutorizacaoResumidaResponseDto>builder()
                .conteudo(conteudo)
                .paginaAtual(pageAutorizacoes.getNumber())
                .totalPaginas(pageAutorizacoes.getTotalPages())
                .totalElementos(pageAutorizacoes.getTotalElements())
                .tamanho(pageAutorizacoes.getSize())
                .build();
    }

    /**
     * Constrói um objeto Pageable com ordenação customizável.
     *
     * @param pagina número da página
     * @param tamanho tamanho da página
     * @param ordenarPor string no formato "campo,direcao" (ex: "dataCriacao,desc")
     * @return Pageable configurado
     */
    private Pageable construirPageable(Integer pagina, Integer tamanho, String ordenarPor) {
        String campoOrdenacao = CAMPO_ORDENACAO_PADRAO;
        Sort.Direction direcao = DIRECAO_PADRAO;

        if (ordenarPor != null && !ordenarPor.isBlank()) {
            String[] partes = ordenarPor.split(",");
            if (partes.length >= 1) {
                // Mapear campo do DTO para campo da entidade
                campoOrdenacao = mapearCampoDTO(partes[0].trim());
            }
            if (partes.length >= 2) {
                try {
                    direcao = Sort.Direction.fromString(partes[1].trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    // Se direção inválida, usar padrão
                    direcao = DIRECAO_PADRAO;
                }
            }
        }

        Sort sort = Sort.by(direcao, campoOrdenacao);
        return PageRequest.of(pagina, tamanho, sort);
    }

    /**
     * Mapeia nomes de campos do DTO para nomes de campos da entidade.
     * 
     * @param campoDtoOuEntidade campo que pode vir do DTO ou ser já da entidade
     * @return nome do campo na entidade Autorizacao
     */
    private String mapearCampoDTO(String campoDtoOuEntidade) {
        return switch (campoDtoOuEntidade) {
            case "dataCriacao" -> "dataHoraInclusao";
            case "valor" -> "valorAutorizacao";
            case "idAutorizacao" -> "idAutorizacao.idAutorizacao";
            case "dataInicioVigencia" -> "dataInicioVigencia";
            case "dataFimVigencia" -> "dataFimVigencia";
            case "idPessoaRecebedora" -> "idPessoaRecebedora";
            // Se for campo da entidade, retorna como está
            default -> campoDtoOuEntidade;
        };
    }

    /**
     * Converte lista de status em String para Integer.
     * Valida se cada status é um enum válido.
     *
     * @param statuses lista de strings com nomes de status
     * @return lista de valores inteiros dos status
     * @throws BusinessException se algum status for inválido
     */
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
                                                        .map(Enum::name)
                                                        .collect(Collectors.toList()))));
                    }
                })
                .collect(Collectors.toList());
    }
}
