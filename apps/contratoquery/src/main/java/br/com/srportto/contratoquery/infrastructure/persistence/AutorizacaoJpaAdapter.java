package br.com.srportto.contratoquery.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import br.com.srportto.contratoquery.domain.exception.ApplicationException;
import br.com.srportto.contratoquery.domain.model.Autorizacao;
import br.com.srportto.contratoquery.domain.model.PaginaAutorizacoes;
import br.com.srportto.contratoquery.domain.port.out.AutorizacaoRepository;

/**
 * Adaptador de persistência. Localiza uma autorização por id numa cascata de até três níveis
 * (partições disjuntas, cobrindo a tabela inteira) — estratégia de armazenamento que não é regra
 * de negócio (D3): a partição da linha deixa de ser derivável do id porque o expurgo transfere
 * autorizações em estado terminal para a faixa 900–999. Mapeia para o domínio puro no retorno de
 * todos os métodos da porta (D1).
 */
@Component
public class AutorizacaoJpaAdapter implements AutorizacaoRepository {

    private static final Logger log = LoggerFactory.getLogger(AutorizacaoJpaAdapter.class);

    private static final int PARTICAO_MIN = 0;
    private static final int PARTICAO_MAX = 889;

    /** Primeira partição da faixa de expurgo; abaixo dela ficam as partições quentes. */
    private static final int PRIMEIRA_PARTICAO_EXPURGO = 900;

    private final SpringDataAutorizacaoRepository springDataRepository;
    private final AutorizacaoPersistenceMapper mapper;

    /** Habilita o nível 3 da cascata; desligar reduz o custo do "não encontrado" ao preço de perder a deteção de anomalia. */
    private final boolean buscaEmParticoesInesperadasHabilitada;

    public AutorizacaoJpaAdapter(
            SpringDataAutorizacaoRepository springDataRepository,
            AutorizacaoPersistenceMapper mapper,
            @Value("${contratoquery.consulta.busca-em-particoes-inesperadas:true}")
            boolean buscaEmParticoesInesperadasHabilitada) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
        this.buscaEmParticoesInesperadasHabilitada = buscaEmParticoesInesperadasHabilitada;
    }

    @Override
    public Optional<Autorizacao> buscarPorId(UUID autorizacaoId) {
        int idParticaoConta;
        try {
            idParticaoConta = ReversibleUUIDv7.extract(autorizacaoId);
        } catch (IllegalArgumentException e) {
            // id inválido ou fora da faixa quente -> "não encontrado" sem tocar no banco
            return Optional.empty();
        }

        if (idParticaoConta < PARTICAO_MIN || idParticaoConta > PARTICAO_MAX) {
            return Optional.empty();
        }

        return localizar(autorizacaoId, idParticaoConta).map(mapper::paraDominio);
    }

    private Optional<AutorizacaoJpaEntity> localizar(UUID autorizacaoId, int particaoDoId) {
        // Nível 1 — partição derivada do id. Caso dominante: autorização ativa, 1 partição.
        Optional<AutorizacaoJpaEntity> naParticaoDoId =
                springDataRepository.findById(new IdAutorizacaoJpaEmbeddable(autorizacaoId, particaoDoId));
        if (naParticaoDoId.isPresent()) {
            return naParticaoDoId;
        }

        // Nível 2 — faixa de expurgo. A autorização chegou a um estado terminal e foi transferida.
        Optional<AutorizacaoJpaEntity> noExpurgo = umaUnica(
                springDataRepository.buscarNaFaixaDeExpurgo(autorizacaoId, PRIMEIRA_PARTICAO_EXPURGO),
                autorizacaoId);
        if (noExpurgo.isPresent()) {
            return noExpurgo;
        }

        // Nível 3 — demais partições quentes. Rede de segurança contra invariante violado.
        if (buscaEmParticoesInesperadasHabilitada) {
            Optional<AutorizacaoJpaEntity> foraDoEsperado = umaUnica(
                    springDataRepository.buscarEmOutrasParticoesQuentes(
                            autorizacaoId, PRIMEIRA_PARTICAO_EXPURGO, particaoDoId),
                    autorizacaoId);
            if (foraDoEsperado.isPresent()) {
                log.warn("Autorização {} encontrada na partição {}, mas o id aponta para a partição {} "
                                + "e ela não está na faixa de expurgo — invariante de localização violado, "
                                + "requer investigação",
                        autorizacaoId,
                        foraDoEsperado.get().getIdAutorizacao().getIdParticaoConta(),
                        particaoDoId);
                return foraDoEsperado;
            }
        }

        return Optional.empty();
    }

    /** Mais de uma linha para o mesmo id é corrupção (resíduo de transferência interrompida) — não desempata, lança erro. */
    private Optional<AutorizacaoJpaEntity> umaUnica(List<AutorizacaoJpaEntity> encontradas, UUID autorizacaoId) {
        if (encontradas.size() > 1) {
            var particoes = encontradas.stream()
                    .map(a -> a.getIdAutorizacao().getIdParticaoConta())
                    .toList();
            log.error("Autorização {} encontrada em mais de uma partição: {} — duplicidade entre "
                    + "partições, nenhuma linha foi escolhida", autorizacaoId, particoes);
            throw new ApplicationException(
                    "Autorização " + autorizacaoId + " presente em mais de uma partição: " + particoes);
        }
        return encontradas.stream().findFirst();
    }

    @Override
    public PaginaAutorizacoes listarPorConta(
            UUID idUnicoContaContratante,
            List<Integer> statusCodigos,
            int pagina,
            int tamanho,
            String campoOrdenacaoJpa,
            boolean ordenacaoAscendente) {

        Sort.Direction direcao = ordenacaoAscendente ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(direcao, campoOrdenacaoJpa));

        Page<AutorizacaoJpaEntity> paginaJpa = (statusCodigos == null || statusCodigos.isEmpty())
                ? springDataRepository.findByIdUnicoContaContratante(idUnicoContaContratante, pageable)
                : springDataRepository.findByIdUnicoContaContratanteAndStatusIn(
                        idUnicoContaContratante, statusCodigos, pageable);

        List<Autorizacao> conteudo = paginaJpa.getContent().stream()
                .map(mapper::paraDominio)
                .toList();

        return new PaginaAutorizacoes(conteudo, paginaJpa.getTotalElements());
    }
}
