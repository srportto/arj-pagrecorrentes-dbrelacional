package br.com.srportto.contratoquery.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.exception.ResourceNotFoundException;
import br.com.srportto.contratoquery.domain.model.Autorizacao;
import br.com.srportto.contratoquery.domain.port.out.AutorizacaoRepository;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * A cascata de partições, antes exercitada aqui, mudou de camada (D3) — agora é coberta por
 * {@code AutorizacaoJpaAdapterTest}. Este teste cobre só o que sobrou no caso de uso: achar × não
 * achar → {@link ResourceNotFoundException}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do ConsultarAutorizacaoService")
class ConsultarAutorizacaoServiceTest {

    @Mock
    private AutorizacaoRepository repository;

    private ConsultarAutorizacaoService service;

    @BeforeEach
    void setUp() {
        service = new ConsultarAutorizacaoService(repository);
    }

    @Test
    @DisplayName("Encontrada: devolve o modelo de domínio recebido da porta")
    void encontrada_DevolveModelo() {
        UUID id = UUID.randomUUID();
        Autorizacao autorizacao = Autorizacao.builder().idAutorizacao(id).build();
        when(repository.buscarPorId(id)).thenReturn(Optional.of(autorizacao));

        Autorizacao resultado = service.consultarPorId(id);

        assertSame(autorizacao, resultado);
    }

    @Test
    @DisplayName("Não encontrada: traduz Optional.empty() em ResourceNotFoundException")
    void naoEncontrada_LancaResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(repository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.consultarPorId(id));
    }
}
