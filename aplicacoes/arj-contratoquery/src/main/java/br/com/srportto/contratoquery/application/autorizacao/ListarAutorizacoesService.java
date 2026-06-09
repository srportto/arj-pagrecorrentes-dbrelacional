package br.com.srportto.contratoquery.application.autorizacao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.srportto.contratoquery.domain.entities.Autorizacao;
import br.com.srportto.contratoquery.domain.enums.StatusAutorizacao;
import br.com.srportto.contratoquery.entrypoint.contratosrest.AutorizacaoResumidaResponseDto;
import br.com.srportto.contratoquery.entrypoint.contratosrest.PaginacaoResponseDto;
import br.com.srportto.contratoquery.shared.exceptions.BusinessException;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ListarAutorizacoesService {

    private final AutorizacaoQueryRepository autorizacaoQueryRepository;

    private static final Integer PAGINA_PADRAO = 0;
    private static final Integer TAMANHO_PADRAO = 20;
    private static final String CAMPO_ORDENACAO_PADRAO = "dataHoraInclusao";
    private static final Sort.Direction DIRECAO_PADRAO = Sort.Direction.DESC;

    public PaginacaoResponseDto<AutorizacaoResumidaResponseDto> listar(
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

        Pageable pageable = construirPageable(paginaFinal, tamanhoFinal, ordenarPor);

        Page<Autorizacao> pageAutorizacoes;
        if (statuses == null || statuses.isEmpty()) {
            pageAutorizacoes = autorizacaoQueryRepository.findByIdUnicoContaContratante(
                    idUnicoContaContratante, pageable);
        } else {
            List<Integer> statusInteiros = converterStatusParaInteiros(statuses);
            pageAutorizacoes = autorizacaoQueryRepository.findByIdUnicoContaContratanteAndStatusIn(
                    idUnicoContaContratante, statusInteiros, pageable);
        }

        List<AutorizacaoResumidaResponseDto> conteudo = pageAutorizacoes.getContent()
                .stream()
                .map(AutorizacaoResumidaResponseDto::from)
                .collect(Collectors.toList());

        return PaginacaoResponseDto.<AutorizacaoResumidaResponseDto>builder()
                .conteudo(conteudo)
                .paginaAtual(pageAutorizacoes.getNumber())
                .totalPaginas(pageAutorizacoes.getTotalPages())
                .totalElementos(pageAutorizacoes.getTotalElements())
                .tamanho(pageAutorizacoes.getSize())
                .build();
    }

    private Pageable construirPageable(Integer pagina, Integer tamanho, String ordenarPor) {
        String campoOrdenacao = CAMPO_ORDENACAO_PADRAO;
        Sort.Direction direcao = DIRECAO_PADRAO;

        if (ordenarPor != null && !ordenarPor.isBlank()) {
            String[] partes = ordenarPor.split(",");
            if (partes.length >= 1) {
                campoOrdenacao = mapearCampoDTO(partes[0].trim());
            }
            if (partes.length >= 2) {
                try {
                    direcao = Sort.Direction.fromString(partes[1].trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    direcao = DIRECAO_PADRAO;
                }
            }
        }

        Sort sort = Sort.by(direcao, campoOrdenacao);
        return PageRequest.of(pagina, tamanho, sort);
    }

    private String mapearCampoDTO(String campoDtoOuEntidade) {
        return switch (campoDtoOuEntidade) {
            case "dataCriacao" -> "dataHoraInclusao";
            case "valor" -> "valorAutorizacao";
            case "idAutorizacao" -> "idAutorizacao.idAutorizacao";
            case "dataInicioVigencia" -> "dataInicioVigencia";
            case "dataFimVigencia" -> "dataFimVigencia";
            case "idPessoaRecebedora" -> "idPessoaRecebedora";
            default -> campoDtoOuEntidade;
        };
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
                                                        .map(Enum::name)
                                                        .collect(Collectors.toList()))));
                    }
                })
                .collect(Collectors.toList());
    }
}
