package br.com.srportto.contratocommand.application;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import br.com.srportto.contratocommand.domain.utilities.ControleExpurgoAutorizacao;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do ExpurgoAutorizacaoService")
class ExpurgoAutorizacaoServiceTest {

    private static final int PARTICAO_ATUAL = 950;

    @Mock
    private AutorizacaoRepository repository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ExpurgoAutorizacaoService service;

    @Test
    @DisplayName("partição de destino diferente da atual: delete -> flush -> detach -> save na nova partição")
    void transferePartiacaoDiferente() {
        var idAutorizacao = new IdAutorizacao(java.util.UUID.randomUUID(), PARTICAO_ATUAL);
        var autorizacao = new Autorizacao();
        autorizacao.setIdAutorizacao(idAutorizacao);

        LocalDate dataReferencia = LocalDate.of(2026, 8, 9);
        int particaoEsperada = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(dataReferencia);
        assertNotEqualsPartition(particaoEsperada, PARTICAO_ATUAL);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Autorizacao resultado = service.transferirParaExpurgo(autorizacao, dataReferencia);

        assertNotNull(resultado);
        assertEquals(particaoEsperada, resultado.getIdAutorizacao().getIdParticaoConta());

        InOrder inOrder = inOrder(repository, entityManager);
        inOrder.verify(repository).deleteById(any());
        inOrder.verify(repository).flush();
        inOrder.verify(entityManager).detach(autorizacao);
        inOrder.verify(repository).save(any());
    }

    @Test
    @DisplayName("partição de destino igual à atual: apenas salva, sem delete+insert")
    void naoTransfereQuandoParticaoIgual() {
        LocalDate dataReferencia = LocalDate.of(2026, 8, 9);
        int particaoEsperada = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(dataReferencia);

        var idAutorizacao = new IdAutorizacao(java.util.UUID.randomUUID(), particaoEsperada);
        var autorizacao = new Autorizacao();
        autorizacao.setIdAutorizacao(idAutorizacao);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Autorizacao resultado = service.transferirParaExpurgo(autorizacao, dataReferencia);

        assertNotNull(resultado);
        assertEquals(particaoEsperada, resultado.getIdAutorizacao().getIdParticaoConta());
        verify(repository, never()).deleteById(any());
        verify(repository, never()).flush();
        verify(entityManager, never()).detach(any());
        verify(repository).save(autorizacao);
    }

    private void assertNotEqualsPartition(int esperada, int atual) {
        if (esperada == atual) {
            throw new IllegalStateException("Fixture inválida: partição esperada coincide com a atual, ajuste PARTICAO_ATUAL");
        }
    }
}
