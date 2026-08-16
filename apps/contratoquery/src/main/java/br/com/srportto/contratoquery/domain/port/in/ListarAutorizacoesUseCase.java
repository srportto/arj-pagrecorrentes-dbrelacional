package br.com.srportto.contratoquery.domain.port.in;

import br.com.srportto.contratoquery.domain.model.Autorizacao;

import java.util.List;
import java.util.UUID;

/** Porta de entrada da listagem paginada. Parâmetros chegam como tipos simples, nunca {@code Pageable} (D7). */
public interface ListarAutorizacoesUseCase {

    ResultadoListagem listar(
            UUID idUnicoContaContratante,
            List<String> statuses,
            Integer pagina,
            Integer tamanho,
            String ordenarPor);

    /** Envelope de paginação em domínio puro — quem traduz para {@code PaginacaoResponseDto} é o controller (D7). */
    record ResultadoListagem(
            List<Autorizacao> conteudo,
            int paginaAtual,
            int tamanho,
            long totalElementos,
            int totalPaginas) {
    }
}
