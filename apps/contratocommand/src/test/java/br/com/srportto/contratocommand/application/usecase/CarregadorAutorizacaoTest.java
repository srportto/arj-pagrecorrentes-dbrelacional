package br.com.srportto.contratocommand.application.usecase;

import br.com.srportto.contratocommand.domain.exception.ApplicationException;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import br.com.srportto.contratocommand.domain.model.Autorizacao;
import br.com.srportto.contratocommand.domain.model.AutorizacaoId;
import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do CarregadorAutorizacao")
class CarregadorAutorizacaoTest {

    @Mock
    private AutorizacaoRepository repository;

    @InjectMocks
    private CarregadorAutorizacao carregador;

    @Test
    @DisplayName("autorização encontrada é devolvida")
    void autorizacaoEncontradaEhDevolvida() {
        UUID uuid = UUID.randomUUID();
        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(uuid);
        when(repository.findById(uuid)).thenReturn(Optional.of(aut));

        Autorizacao resultado = carregador.carregar(new AutorizacaoId(uuid));

        assertSame(aut, resultado);
    }

    @Test
    @DisplayName("autorização ausente lança BusinessException com a mensagem preservada")
    void autorizacaoAusenteLancaBusinessException() {
        UUID uuid = UUID.randomUUID();
        when(repository.findById(uuid)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> carregador.carregar(new AutorizacaoId(uuid)));

        assertNotNull(ex.getMessage());
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains(uuid.toString()));
    }

    @Test
    @DisplayName("ConcurrencyFailureException do repository propaga sem virar ApplicationException (design.md, D3)")
    void concurrencyFailureExceptionPropaga() {
        UUID uuid = UUID.randomUUID();
        OptimisticLockingFailureException conflito = new OptimisticLockingFailureException("versão divergente");
        when(repository.findById(uuid)).thenThrow(conflito);

        OptimisticLockingFailureException ex = assertThrows(OptimisticLockingFailureException.class,
                () -> carregador.carregar(new AutorizacaoId(uuid)));

        assertSame(conflito, ex);
    }

    @Test
    @DisplayName("falha genuinamente inesperada vira ApplicationException preservando a causa")
    void falhaInesperadaViraApplicationException() {
        UUID uuid = UUID.randomUUID();
        RuntimeException causaOriginal = new RuntimeException("Erro de acesso ao banco de dados");
        when(repository.findById(uuid)).thenThrow(causaOriginal);

        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> carregador.carregar(new AutorizacaoId(uuid)));

        assertSame(causaOriginal, ex.getCause());
    }
}
