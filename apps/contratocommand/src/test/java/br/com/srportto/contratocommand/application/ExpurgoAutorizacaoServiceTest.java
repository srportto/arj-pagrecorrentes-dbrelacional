package br.com.srportto.contratocommand.application;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O cálculo de partição e o row movement moraram aqui e migraram para
 * {@code AutorizacaoJpaAdapter} (D4 da change hexagonal-classico-contratocommand-portas); este
 * serviço agora só delega à porta.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do ExpurgoAutorizacaoService")
class ExpurgoAutorizacaoServiceTest {

    private static final LocalDate DATA_REFERENCIA = LocalDate.of(2026, 8, 9);

    @Mock
    private AutorizacaoRepository repository;

    @InjectMocks
    private ExpurgoAutorizacaoService service;

    @Test
    @DisplayName("delega a transferencia para a porta de saida")
    void delegaParaAPorta() {
        var autorizacao = new Autorizacao();
        autorizacao.setIdAutorizacao(new IdAutorizacao(UUID.randomUUID(), 5));
        var transferida = new Autorizacao();
        when(repository.transferirParaExpurgo(autorizacao, DATA_REFERENCIA)).thenReturn(transferida);

        var resultado = service.transferirParaExpurgo(autorizacao, DATA_REFERENCIA);

        assertSame(transferida, resultado);
        verify(repository).transferirParaExpurgo(autorizacao, DATA_REFERENCIA);
    }
}
