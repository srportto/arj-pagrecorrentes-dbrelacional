package br.com.srportto.contratoquery.application.autorizacao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.srportto.contratoquery.domain.entities.Autorizacao;
import br.com.srportto.contratoquery.domain.entities.IdAutorizacao;
import br.com.srportto.contratoquery.domain.utilities.ReversibleUUIDv7;
import br.com.srportto.contratoquery.entrypoint.contratosrest.AutorizacaoDetalheResponseDto;
import br.com.srportto.contratoquery.shared.exceptions.ApplicationException;
import br.com.srportto.contratoquery.shared.exceptions.ResourceNotFoundException;

/**
 * Localiza uma autorização por id numa cascata de até três níveis (partições disjuntas, cobrindo
 * a tabela inteira). Necessária porque a partição da linha deixa de ser derivável do id: o
 * {@code ReversibleUUIDv7} carrega a partição de criação, imutável, mas o expurgo transfere
 * autorizações em estado terminal para a faixa 900–999. Sem a cascata, {@code GET /{id}} devolve
 * 404 para toda autorização cancelada, rejeitada ou expirada.
 */
@Service
public class ConsultarAutorizacaoService {

    private static final Logger log = LoggerFactory.getLogger(ConsultarAutorizacaoService.class);

    private static final int PARTICAO_MIN = 0;
    private static final int PARTICAO_MAX = 889;

    /** Primeira partição da faixa de expurgo; abaixo dela ficam as partições quentes. */
    private static final int PRIMEIRA_PARTICAO_EXPURGO = 900;

    private final AutorizacaoRepository repository;

    /** Habilita o nível 3 da cascata; desligar reduz o custo do "não encontrado" ao preço de perder a deteção de anomalia. */
    private final boolean buscaEmParticoesInesperadasHabilitada;

    public ConsultarAutorizacaoService(
            AutorizacaoRepository repository,
            @Value("${contratoquery.consulta.busca-em-particoes-inesperadas:true}")
            boolean buscaEmParticoesInesperadasHabilitada) {
        this.repository = repository;
        this.buscaEmParticoesInesperadasHabilitada = buscaEmParticoesInesperadasHabilitada;
    }

    @Transactional(readOnly = true)
    public AutorizacaoDetalheResponseDto consultarPorId(UUID autorizacaoId) {
        int idParticaoConta = extrairParticao(autorizacaoId);

        return AutorizacaoDetalheResponseDto.from(localizar(autorizacaoId, idParticaoConta));
    }

    private Autorizacao localizar(UUID autorizacaoId, int particaoDoId) {
        // Nível 1 — partição derivada do id. Caso dominante: autorização ativa, 1 partição.
        Optional<Autorizacao> naParticaoDoId =
                repository.findById(new IdAutorizacao(autorizacaoId, particaoDoId));
        if (naParticaoDoId.isPresent()) {
            return naParticaoDoId.get();
        }

        // Nível 2 — faixa de expurgo. A autorização chegou a um estado terminal e foi transferida.
        Optional<Autorizacao> noExpurgo = umaUnica(
                repository.buscarNaFaixaDeExpurgo(autorizacaoId, PRIMEIRA_PARTICAO_EXPURGO),
                autorizacaoId);
        if (noExpurgo.isPresent()) {
            return noExpurgo.get();
        }

        // Nível 3 — demais partições quentes. Rede de segurança contra invariante violado.
        if (buscaEmParticoesInesperadasHabilitada) {
            Optional<Autorizacao> foraDoEsperado = umaUnica(
                    repository.buscarEmOutrasParticoesQuentes(
                            autorizacaoId, PRIMEIRA_PARTICAO_EXPURGO, particaoDoId),
                    autorizacaoId);
            if (foraDoEsperado.isPresent()) {
                log.warn("Autorização {} encontrada na partição {}, mas o id aponta para a partição {} "
                                + "e ela não está na faixa de expurgo — invariante de localização violado, "
                                + "requer investigação",
                        autorizacaoId,
                        foraDoEsperado.get().getIdAutorizacao().getIdParticaoConta(),
                        particaoDoId);
                return foraDoEsperado.get();
            }
        }

        throw autorizacaoNaoEncontrada(autorizacaoId);
    }

    /** Mais de uma linha para o mesmo id é corrupção (resíduo de transferência interrompida) — não desempata, lança erro. */
    private Optional<Autorizacao> umaUnica(List<Autorizacao> encontradas, UUID autorizacaoId) {
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

    /** Deriva a partição do UUID; id inválido ou fora da faixa quente -> 404 sem tocar no banco. */
    private int extrairParticao(UUID autorizacaoId) {
        int idParticaoConta;
        try {
            idParticaoConta = ReversibleUUIDv7.extract(autorizacaoId);
        } catch (IllegalArgumentException e) {
            throw autorizacaoNaoEncontrada(autorizacaoId);
        }

        if (idParticaoConta < PARTICAO_MIN || idParticaoConta > PARTICAO_MAX) {
            throw autorizacaoNaoEncontrada(autorizacaoId);
        }

        return idParticaoConta;
    }

    private ResourceNotFoundException autorizacaoNaoEncontrada(UUID autorizacaoId) {
        return new ResourceNotFoundException(
                String.format("Autorizacao nao encontrada para o id %s", autorizacaoId));
    }
}
