package br.com.srportto.contratocommand.infrastructure.persistence;

import br.com.srportto.contratocommand.domain.model.Autorizacao;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre só o que é verificável sem banco (escolha de partição, atalho, guarda de linhas afetadas).
 * Comportamento real coberto por {@code ExpurgoParticaoIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do AutorizacaoJpaAdapter.transferirParaExpurgo")
class AutorizacaoJpaAdapterTest {

    private static final LocalDate DATA_REFERENCIA = LocalDate.of(2026, 8, 9);
    private static final int PARTICAO_DESTINO =
            ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(DATA_REFERENCIA);
    private static final int PARTICAO_QUENTE = 5;

    @Mock
    private SpringDataAutorizacaoRepository springDataRepository;
    @Mock
    private AutorizacaoPersistenceMapper mapper;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AutorizacaoJpaAdapter adapter;

    @Test
    @DisplayName("particao de destino diferente da atual: move a linha e devolve a autorizacao na nova particao")
    void particaoDiferente_MoveALinha() {
        var autorizacao = autorizacaoNaParticao(PARTICAO_QUENTE);
        UUID id = autorizacao.getIdAutorizacao();
        var entidadeGerenciada = entidadeNaParticao(id, PARTICAO_QUENTE);
        when(springDataRepository.findByIdAutorizacaoAndParticao(id, PARTICAO_QUENTE))
                .thenReturn(Optional.of(entidadeGerenciada));
        when(springDataRepository.saveAndFlush(entidadeGerenciada)).thenReturn(entidadeGerenciada);
        when(springDataRepository.moverParaParticao(id, PARTICAO_QUENTE, PARTICAO_DESTINO)).thenReturn(1);
        var autorizacaoRetorno = autorizacaoNaParticao(PARTICAO_DESTINO);
        when(mapper.paraDominio(entidadeGerenciada)).thenReturn(autorizacaoRetorno);

        Autorizacao resultado = adapter.transferirParaExpurgo(autorizacao, DATA_REFERENCIA);

        assertNotNull(resultado);
        assertEquals(PARTICAO_DESTINO, resultado.getIdParticaoConta(),
                "A autorizacao devolvida deve refletir a nova localizacao fisica");
        verify(mapper).aplicarEm(autorizacao, entidadeGerenciada);
        verify(springDataRepository).moverParaParticao(id, PARTICAO_QUENTE, PARTICAO_DESTINO);
        verify(entityManager).detach(entidadeGerenciada);
    }

    @Test
    @DisplayName("particao de destino igual a atual: apenas salva, sem mover a linha")
    void particaoIgual_ApenasSalva() {
        var autorizacao = autorizacaoNaParticao(PARTICAO_DESTINO);
        UUID id = autorizacao.getIdAutorizacao();
        var entidadeGerenciada = entidadeNaParticao(id, PARTICAO_DESTINO);
        when(springDataRepository.findByIdAutorizacaoAndParticao(id, PARTICAO_DESTINO))
                .thenReturn(Optional.of(entidadeGerenciada));
        when(springDataRepository.save(entidadeGerenciada)).thenReturn(entidadeGerenciada);
        when(mapper.paraDominio(entidadeGerenciada)).thenReturn(autorizacao);

        Autorizacao resultado = adapter.transferirParaExpurgo(autorizacao, DATA_REFERENCIA);

        assertNotNull(resultado);
        verify(mapper).aplicarEm(autorizacao, entidadeGerenciada);
        verify(springDataRepository).save(entidadeGerenciada);
        verify(springDataRepository, never()).moverParaParticao(any(), anyInt(), anyInt());
        verify(entityManager, never()).detach(any());
    }

    @Test
    @DisplayName("movimentacao que nao afeta exatamente uma linha aborta a transacao")
    void movimentacaoSemLinhaAfetada_Falha() {
        var autorizacao = autorizacaoNaParticao(PARTICAO_QUENTE);
        UUID id = autorizacao.getIdAutorizacao();
        var entidadeGerenciada = entidadeNaParticao(id, PARTICAO_QUENTE);
        when(springDataRepository.findByIdAutorizacaoAndParticao(id, PARTICAO_QUENTE))
                .thenReturn(Optional.of(entidadeGerenciada));
        when(springDataRepository.saveAndFlush(entidadeGerenciada)).thenReturn(entidadeGerenciada);
        when(springDataRepository.moverParaParticao(id, PARTICAO_QUENTE, PARTICAO_DESTINO)).thenReturn(0);

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> adapter.transferirParaExpurgo(autorizacao, DATA_REFERENCIA),
                "Linha ausente na origem so pode significar remocao concorrente — nao pode passar calada");

        verify(entityManager, never()).detach(any());
    }

    @Test
    @DisplayName("autorizacao gerenciada ausente na particao esperada aborta com falha de concorrencia")
    void entidadeGerenciadaAusente_Falha() {
        var autorizacao = autorizacaoNaParticao(PARTICAO_QUENTE);
        UUID id = autorizacao.getIdAutorizacao();
        when(springDataRepository.findByIdAutorizacaoAndParticao(id, PARTICAO_QUENTE))
                .thenReturn(Optional.empty());

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> adapter.transferirParaExpurgo(autorizacao, DATA_REFERENCIA));
    }

    private Autorizacao autorizacaoNaParticao(int particao) {
        var autorizacao = new Autorizacao();
        // ReversibleUUIDv7.extract exige UUIDv7 — UUID.randomUUID() (v4) quebraria o adaptador antes do mock.
        autorizacao.setIdAutorizacao(ReversibleUUIDv7.generate(particao));
        autorizacao.setIdParticaoConta(particao);
        return autorizacao;
    }

    private AutorizacaoJpaEntity entidadeNaParticao(UUID id, int particao) {
        var entidade = new AutorizacaoJpaEntity();
        entidade.setIdAutorizacao(new IdAutorizacaoJpaEmbeddable(id, particao));
        return entidade;
    }
}
