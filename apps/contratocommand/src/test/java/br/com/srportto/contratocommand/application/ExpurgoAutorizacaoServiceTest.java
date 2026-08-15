package br.com.srportto.contratocommand.application;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import br.com.srportto.contratocommand.domain.utilities.ControleExpurgoAutorizacao;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;
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
 * A versão anterior afirmava a *ordem* das chamadas e ficou verde mesmo com toda transferência
 * falhando 409 em banco real — um teste de sequência não detecta defeito na decisão que o
 * provedor JPA toma. Comportamento real coberto por {@code ExpurgoParticaoIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do ExpurgoAutorizacaoService")
class ExpurgoAutorizacaoServiceTest {

    private static final LocalDate DATA_REFERENCIA = LocalDate.of(2026, 8, 9);
    private static final int PARTICAO_DESTINO =
            ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(DATA_REFERENCIA);
    private static final int PARTICAO_QUENTE = 5;

    @Mock
    private AutorizacaoRepository repository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ExpurgoAutorizacaoService service;

    @Test
    @DisplayName("particao de destino diferente da atual: move a linha e devolve a autorizacao na nova particao")
    void particaoDiferente_MoveALinha() {
        var autorizacao = autorizacaoNaParticao(PARTICAO_QUENTE);
        UUID id = autorizacao.getIdAutorizacao().getIdAutorizacao();
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.moverParaParticao(id, PARTICAO_QUENTE, PARTICAO_DESTINO)).thenReturn(1);

        Autorizacao resultado = service.transferirParaExpurgo(autorizacao, DATA_REFERENCIA);

        assertNotNull(resultado);
        assertEquals(PARTICAO_DESTINO, resultado.getIdAutorizacao().getIdParticaoConta(),
                "A autorizacao devolvida deve refletir a nova localizacao fisica");
        verify(repository).moverParaParticao(id, PARTICAO_QUENTE, PARTICAO_DESTINO);
        verify(entityManager).detach(autorizacao);
    }

    @Test
    @DisplayName("particao de destino igual a atual: apenas salva, sem mover a linha")
    void particaoIgual_ApenasSalva() {
        var autorizacao = autorizacaoNaParticao(PARTICAO_DESTINO);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Autorizacao resultado = service.transferirParaExpurgo(autorizacao, DATA_REFERENCIA);

        assertNotNull(resultado);
        assertEquals(PARTICAO_DESTINO, resultado.getIdAutorizacao().getIdParticaoConta());
        verify(repository).save(autorizacao);
        verify(repository, never()).moverParaParticao(any(), anyInt(), anyInt());
        verify(entityManager, never()).detach(any());
    }

    @Test
    @DisplayName("movimentacao que nao afeta exatamente uma linha aborta a transacao")
    void movimentacaoSemLinhaAfetada_Falha() {
        var autorizacao = autorizacaoNaParticao(PARTICAO_QUENTE);
        UUID id = autorizacao.getIdAutorizacao().getIdAutorizacao();
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.moverParaParticao(id, PARTICAO_QUENTE, PARTICAO_DESTINO)).thenReturn(0);

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> service.transferirParaExpurgo(autorizacao, DATA_REFERENCIA),
                "Linha ausente na origem so pode significar remocao concorrente — nao pode passar calada");

        verify(entityManager, never()).detach(any());
        assertEquals(PARTICAO_QUENTE, autorizacao.getIdAutorizacao().getIdParticaoConta(),
                "A instancia nao pode ser marcada como transferida quando a movimentacao falhou");
    }

    private Autorizacao autorizacaoNaParticao(int particao) {
        var autorizacao = new Autorizacao();
        autorizacao.setIdAutorizacao(new IdAutorizacao(UUID.randomUUID(), particao));
        return autorizacao;
    }
}
