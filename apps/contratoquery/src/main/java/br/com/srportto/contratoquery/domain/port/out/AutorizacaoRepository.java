package br.com.srportto.contratoquery.domain.port.out;

import br.com.srportto.contratoquery.domain.model.Autorizacao;
import br.com.srportto.contratoquery.domain.model.PaginaAutorizacoes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída de leitura de {@link Autorizacao} — sem dependência de Spring Data e sem
 * vazamento de esquema de particionamento na assinatura (D3, D4).
 */
public interface AutorizacaoRepository {

    /** Busca por UUID. A cascata de localização em partições é responsabilidade exclusiva do adaptador (D3). */
    Optional<Autorizacao> buscarPorId(UUID idAutorizacao);

    /**
     * Lista autorizações de uma conta, com filtro opcional de status e ordenação já resolvida
     * pelo caso de uso. Devolve conteúdo + total (D7) — nunca {@code Page} do Spring Data.
     */
    PaginaAutorizacoes listarPorConta(
            UUID idUnicoContaContratante,
            List<Integer> statusCodigos,
            int pagina,
            int tamanho,
            String campoOrdenacaoJpa,
            boolean ordenacaoAscendente);
}
