package br.com.srportto.contratocommand.application.pixauto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratocommand.application.enabledproduct.pixauto.PixAutoService;
import br.com.srportto.contratocommand.application.enabledproduct.pixauto.usecases.CancelarPixAutoUseCase;
import br.com.srportto.contratocommand.application.enabledproduct.pixauto.usecases.CriarPixAutoUseCase;
import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;

@ExtendWith(MockitoExtension.class)
class PixAutoAutorizacaoServiceTest {

    @InjectMocks
    private PixAutoService service;

    @Mock
    private CriarPixAutoUseCase criarPixAutoUseCase;

    @Mock
    private CancelarPixAutoUseCase cancelarPixAutoUseCase;

    private static final Integer STATUS_ATIVA = 1;

    @Test
    @DisplayName("validaContratacaoSuportada - deve retornar true para TipoProduto.PIX_AUTO")
    void testValidaContratacaoSuportada_True() {
        CriarAutorizacaoRequest request = new CriarAutorizacaoRequest(
                LocalDate.now().plusDays(30),
                "PIX_AUTO",
                new BigDecimal("1000.00"),
                "EMP001",
                new BigDecimal("2000.00"),
                5,
                2,
                0,
                "C1",
                "Teste",
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440002"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440003"),
                null
        );
        assertTrue(service.validaContratacaoSuportada(request));
    }

    @Test
    @DisplayName("validaContratacaoSuportada - deve retornar false para TipoProduto.DDA_AUTO")
    void testValidaContratacaoSuportada_False() {
        CriarAutorizacaoRequest request = new CriarAutorizacaoRequest(
                LocalDate.now().plusDays(30),
                "DDA_AUTO",
                new BigDecimal("1000.00"),
                "EMP001",
                new BigDecimal("2000.00"),
                5,
                2,
                0,
                "C1",
                "Teste",
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440002"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440003"),
                null
        );
        assertFalse(service.validaContratacaoSuportada(request));
    }

    @Test
    @DisplayName("validaContratacaoSuportada - deve retornar false para tipoProduto nulo")
    void testValidaContratacaoSuportada_NullTipoProduto() {
        CriarAutorizacaoRequest request = new CriarAutorizacaoRequest(
                LocalDate.now().plusDays(30),
                null,
                new BigDecimal("1000.00"),
                "EMP001",
                new BigDecimal("2000.00"),
                5,
                2,
                0,
                "C1",
                "Teste",
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440002"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440003"),
                null
        );
        assertFalse(service.validaContratacaoSuportada(request));
    }

    @Test
    @DisplayName("criarAutorizacao - delega ao CriarPixAutoUseCase")
    void testCriarAutorizacao_Sucesso() {
        // Arrange
        CriarAutorizacaoRequest request = new CriarAutorizacaoRequest(
                LocalDate.now().plusDays(30),
                "PIX_AUTO",
                new BigDecimal("1000.00"),
                "EMP001",
                new BigDecimal("2000.00"),
                5,
                2,
                0,
                "C1",
                "Teste",
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440002"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440003"),
                null
        );

        Autorizacao autorizacao = criarAutorizacao();
        AutorizacaoCompletaResponseDto responseDto = AutorizacaoCompletaResponseDto.from(autorizacao);
        
        when(criarPixAutoUseCase.execute(request)).thenReturn(responseDto);

        // Act
        AutorizacaoCompletaResponseDto resultado = service.criarAutorizacao(request);

        // Assert
        assertNotNull(resultado);
        verify(criarPixAutoUseCase).execute(request);
    }

    @Test
    @DisplayName("cancelarAutorizacao - delega ao CancelarPixAutoUseCase")
    void testCancelarAutorizacao_Sucesso() {
        // Arrange
        CancelarAutorizacaoRequestDto request = CancelarAutorizacaoRequestDto.builder()
                .idAutorizacao("550e8400-e29b-41d4-a716-446655440000")
                .codigoCanalCancelamento("C1")
                .idPessoaCancelamento(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
                .motivoCancelamento("Teste cancelamento")
                .produtoHeaderRequest(TipoProduto.PIX_AUTO)
                .build();

        Autorizacao autorizacao = criarAutorizacao();
        AutorizacaoCompletaResponseDto responseDto = AutorizacaoCompletaResponseDto.from(autorizacao);
        
        when(cancelarPixAutoUseCase.execute(request)).thenReturn(responseDto);

        // Act
        AutorizacaoCompletaResponseDto resultado = service.cancelarAutorizacao(request);

        // Assert
        assertNotNull(resultado);
        verify(cancelarPixAutoUseCase).execute(request);
    }

    // Helpers
    private Autorizacao criarAutorizacao() {
        IdAutorizacao id = new IdAutorizacao();
        id.setIdAutorizacao(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        id.setIdParticaoConta(1);

        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(id);
        aut.setValorAutorizacao(new BigDecimal("1000.00"));
        aut.setValorLimite(new BigDecimal("2000.00"));
        aut.setIdAutorizacaoEmpresa("EMP001");
        aut.setStatus(STATUS_ATIVA);
        aut.setMotivoStatus("Ativa");
        aut.setDataFimVigencia(LocalDate.of(9999, 1, 1));
        aut.setDataInicioVigencia(LocalDate.now());
        aut.setDataHoraInclusao(LocalDateTime.now());
        aut.setDataHoraUltimaAtualizacao(LocalDateTime.now());
        aut.setFrequenciaPagamento((short) 5);
        aut.setQuantidadeDividasCiclo((short) 2);
        aut.setIndicadorUsoLimiteConta((short) 0);
        aut.setIndicadorTipoMensageria((short) 0);
        aut.setCodigoCanalContratacao("C1");
        aut.setDescricao("Teste");
        aut.setIdUnicoContaContratante(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        aut.setIdPessoaPagadora(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
        aut.setIdPessoaDevedora(UUID.fromString("550e8400-e29b-41d4-a716-446655440002"));
        aut.setIdPessoaRecebedora(UUID.fromString("550e8400-e29b-41d4-a716-446655440003"));
        aut.setMetadados("{}");

        return aut;
    }
}
