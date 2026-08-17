package br.com.srportto.contratoquery.domain.port.out;

import br.com.srportto.contratoquery.domain.enums.CampoOrdenacao;
import br.com.srportto.contratoquery.domain.enums.StatusAutorizacao;
import br.com.srportto.contratoquery.domain.model.Autorizacao;
import br.com.srportto.contratoquery.domain.model.PaginaAutorizacoes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída de leitura de {@link Autorizacao} — sem dependência de Spring Data e sem
 * vazamento de esquema de particionamento nem de vocabulário de persistência na assinatura
 * (D3, D4): filtro e ordenação trafegam como enum de domínio, nunca como caminho de propriedade
 * JPA ou código numérico de banco — a tradução para os dois é responsabilidade exclusiva do
 * adaptador.
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
            List<StatusAutorizacao> statuses,
            int pagina,
            int tamanho,
            CampoOrdenacao campoOrdenacao,
            boolean ordenacaoAscendente);
}
