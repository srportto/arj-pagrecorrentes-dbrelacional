package br.com.srportto.contratocommand.application.usecase;

import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.service.contratacao.ContratacaoValidator;
import br.com.srportto.contratocommand.domain.event.AutorizacaoPersistidaEvent;
import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.exception.RecursoJaExisteException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do CriarAutorizacaoService")
class CriarAutorizacaoServiceTest {

    @Mock
    private AutorizacaoRepository repository;
    @Mock
    private AutorizacaoMapper mapper;
    @Mock
    private ContratacaoValidator contratacaoValidator;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CriarAutorizacaoService service;

    @Test
    @DisplayName("valida, mapeia, persiste e retorna o modelo")
    void executa() {
        CriarAutorizacaoCommand command = TestFixtures.criarContextPix();
        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(new IdAutorizacao(UUID.randomUUID(), 10));
        when(mapper.toDomain(command)).thenReturn(aut);
        when(repository.save(aut)).thenReturn(aut);

        Autorizacao resp = service.execute(command);

        assertNotNull(resp);
        assertEquals(aut.getIdAutorizacao().getIdAutorizacao(), resp.getIdAutorizacao().getIdAutorizacao());
        verify(contratacaoValidator).validar(command);
        verify(repository).save(aut);
    }

    @Test
    @DisplayName("publica evento de criação com o estado final persistido")
    void publicaEventoDeCriacao() {
        CriarAutorizacaoCommand command = TestFixtures.criarContextPix();
        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(new IdAutorizacao(UUID.randomUUID(), 10));
        when(mapper.toDomain(command)).thenReturn(aut);
        when(repository.save(aut)).thenReturn(aut);

        service.execute(command);

        verify(eventPublisher).publishEvent(new AutorizacaoPersistidaEvent(aut));
    }

    @Test
    @DisplayName("rejeita criação quando id_autorizacao_empresa já existe na mesma partição com RecursoJaExisteException (409)")
    void duplicidadeIdAutorizacaoEmpresa_Rejeitada() {
        CriarAutorizacaoCommand command = TestFixtures.criarContextPix();
        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(new IdAutorizacao(UUID.randomUUID(), 10));
        String idEmpresa = command.idAutorizacaoEmpresa();

        when(repository.existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa(anyInt(), eq(idEmpresa)))
                .thenReturn(true);

        // RecursoJaExisteException (409), não BusinessException (422): recurso já existir é erro
        // distinto de regra de negócio violada.
        RecursoJaExisteException ex = assertThrows(RecursoJaExisteException.class, () -> service.execute(command));
        assertTrue(ex.getMessage().contains("já existe"));

        verify(contratacaoValidator).validar(command);
        verify(repository).existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa(anyInt(), eq(idEmpresa));
        verify(repository, never()).save(any(Autorizacao.class));
        verify(eventPublisher, never()).publishEvent(any(AutorizacaoPersistidaEvent.class));
    }

    @Test
    @DisplayName("criação bem-sucedida quando id_autorizacao_empresa não existe na partição da conta")
    void idAutorizacaoEmpresaNaoExiste_Criada() {
        CriarAutorizacaoCommand command = TestFixtures.criarContextPix();
        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(new IdAutorizacao(UUID.randomUUID(), 10));
        String idEmpresa = command.idAutorizacaoEmpresa();

        when(repository.existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa(anyInt(), eq(idEmpresa)))
                .thenReturn(false);
        when(mapper.toDomain(command)).thenReturn(aut);
        when(repository.save(aut)).thenReturn(aut);

        Autorizacao resp = service.execute(command);

        assertNotNull(resp);
        verify(contratacaoValidator).validar(command);
        verify(repository).existsByIdAutorizacao_IdParticaoContaAndIdAutorizacaoEmpresa(anyInt(), eq(idEmpresa));
        verify(repository).save(aut);
        verify(eventPublisher).publishEvent(new AutorizacaoPersistidaEvent(aut));
    }
}
