package br.com.srportto.contratocommand.application.enabledproduct.pixauto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.IdAutorizacao;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoResumidaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.PaginacaoResponseDto;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do ListarAutorizacoesService")
class ListarAutorizacoesServiceTest {

    @Mock
    private PixAutoRepository pixAutoRepository;

    @InjectMocks
    private ListarAutorizacoesService listarAutorizacoesService;

    private UUID idUnicoContaContratante;
    private Autorizacao autorizacao1;
    private Autorizacao autorizacao2;

    @BeforeEach
    void setUp() {
        idUnicoContaContratante = UUID.randomUUID();
        
        // Criar autorizações de teste
        autorizacao1 = criarAutorizacao(1, 100.00);
        autorizacao2 = criarAutorizacao(4, 500.00);
    }

    private Autorizacao criarAutorizacao(int status, double valor) {
        Autorizacao auto = new Autorizacao();
        auto.setIdAutorizacao(new IdAutorizacao(UUID.randomUUID(), 1));
        auto.setStatus(status);
        auto.setDataHoraInclusao(LocalDateTime.now());
        auto.setDataInicioVigencia(LocalDate.now());
        auto.setDataFimVigencia(LocalDate.now().plusDays(30));
        auto.setValorAutorizacao(BigDecimal.valueOf(valor));
        auto.setIdUnicoContaContratante(idUnicoContaContratante);
        auto.setIdPessoaRecebedora(UUID.randomUUID());
        auto.setMotivoStatus("Teste");
        auto.setFrequenciaPagamento((short) 1);
        auto.setQuantidadeDividasCiclo((short) 1);
        auto.setIndicadorUsoLimiteConta((short) 0);
        auto.setIndicadorTipoMensageria((short) 0);
        auto.setCodigoCanalContratacao("01");
        auto.setIdAutorizacaoEmpresa("EMP001");
        auto.setValorLimite(BigDecimal.valueOf(10000));
        return auto;
    }

    @Test
    @DisplayName("Deve retornar erro 400 quando idUnicoContaContratante é nulo")
    void testListarComIdUnicoContaNulo() {
        assertThrows(BusinessException.class, () -> {
            listarAutorizacoesService.listar(null, null, 0, 20, null);
        });
    }

    @Test
    @DisplayName("Deve listar todas as autorizações sem filtro de status")
    void testListarSemFiltroStatus() {
        // Arrange
        List<Autorizacao> autorizacoes = Arrays.asList(autorizacao1, autorizacao2);
        Page<Autorizacao> pagina = new PageImpl<>(autorizacoes, PageRequest.of(0, 20), 2);
        
        when(pixAutoRepository.findByIdUnicoContaContratante(
                eq(idUnicoContaContratante),
                any(Pageable.class)))
                .thenReturn(pagina);

        // Act
        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado = 
            listarAutorizacoesService.listar(idUnicoContaContratante, null, 0, 20, null);

        // Assert
        assertNotNull(resultado);
        assertEquals(2, resultado.getConteudo().size());
        assertEquals(0, resultado.getPaginaAtual());
        assertEquals(1, resultado.getTotalPaginas());
        assertEquals(2L, resultado.getTotalElementos());
        assertEquals(20, resultado.getTamanho());
        
        verify(pixAutoRepository, times(1)).findByIdUnicoContaContratante(
                eq(idUnicoContaContratante),
                any(Pageable.class));
    }

    @Test
    @DisplayName("Deve listar autorizações com filtro de status")
    void testListarComFiltroStatus() {
        // Arrange
        List<Autorizacao> autorizacoes = Arrays.asList(autorizacao1);
        Page<Autorizacao> pagina = new PageImpl<>(autorizacoes, PageRequest.of(0, 20), 1);
        
        when(pixAutoRepository.findByIdUnicoContaContratanteAndStatusIn(
                eq(idUnicoContaContratante),
                eq(Arrays.asList(1)),
                any(Pageable.class)))
                .thenReturn(pagina);

        // Act
        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado = 
            listarAutorizacoesService.listar(idUnicoContaContratante, Arrays.asList("RECEBIDA"), 0, 20, null);

        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.getConteudo().size());
        assertEquals(0, resultado.getPaginaAtual());
        
        verify(pixAutoRepository, times(1)).findByIdUnicoContaContratanteAndStatusIn(
                eq(idUnicoContaContratante),
                eq(Arrays.asList(1)),
                any(Pageable.class));
    }

    @Test
    @DisplayName("Deve retornar erro quando status é inválido")
    void testListarComStatusInvalido() {
        assertThrows(BusinessException.class, () -> {
            listarAutorizacoesService.listar(idUnicoContaContratante, Arrays.asList("STATUS_INVALIDO"), 0, 20, null);
        });
    }

    @Test
    @DisplayName("Deve aplicar valores padrão de paginação")
    void testListarComValoresPadraoDeAPaginacao() {
        // Arrange
        List<Autorizacao> autorizacoes = Arrays.asList(autorizacao1);
        Page<Autorizacao> pagina = new PageImpl<>(autorizacoes, PageRequest.of(0, 20), 1);
        
        when(pixAutoRepository.findByIdUnicoContaContratante(
                eq(idUnicoContaContratante),
                any(Pageable.class)))
                .thenReturn(pagina);

        // Act
        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado = 
            listarAutorizacoesService.listar(idUnicoContaContratante, null, null, null, null);

        // Assert
        assertNotNull(resultado);
        assertEquals(0, resultado.getPaginaAtual()); // página padrão 0
        assertEquals(20, resultado.getTamanho()); // tamanho padrão 20
        
        verify(pixAutoRepository, times(1)).findByIdUnicoContaContratante(
                eq(idUnicoContaContratante),
                any(Pageable.class));
    }

    @Test
    @DisplayName("Deve retornar lista vazia sem erro quando nenhuma autorização encontrada")
    void testListarSemResultados() {
        // Arrange
        Page<Autorizacao> paginaVazia = new PageImpl<>(Arrays.asList(), PageRequest.of(0, 20), 0);
        
        when(pixAutoRepository.findByIdUnicoContaContratante(
                eq(idUnicoContaContratante),
                any(Pageable.class)))
                .thenReturn(paginaVazia);

        // Act
        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado = 
            listarAutorizacoesService.listar(idUnicoContaContratante, null, 0, 20, null);

        // Assert
        assertNotNull(resultado);
        assertEquals(0, resultado.getConteudo().size());
        assertEquals(0L, resultado.getTotalElementos());
    }

    @Test
    @DisplayName("Deve suportar múltiplos status no filtro")
    void testListarComMultiplosStatus() {
        // Arrange
        List<Autorizacao> autorizacoes = Arrays.asList(autorizacao1, autorizacao2);
        Page<Autorizacao> pagina = new PageImpl<>(autorizacoes, PageRequest.of(0, 20), 2);
        
        when(pixAutoRepository.findByIdUnicoContaContratanteAndStatusIn(
                eq(idUnicoContaContratante),
                eq(Arrays.asList(1, 4)),
                any(Pageable.class)))
                .thenReturn(pagina);

        // Act
        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado = 
            listarAutorizacoesService.listar(idUnicoContaContratante, Arrays.asList("RECEBIDA", "ATIVA"), 0, 20, null);

        // Assert
        assertNotNull(resultado);
        assertEquals(2, resultado.getConteudo().size());
    }

    @Test
    @DisplayName("Deve converter autorização para DTO resumido corretamente")
    void testConversaoParaDtoResumido() {
        // Arrange
        List<Autorizacao> autorizacoes = Arrays.asList(autorizacao1);
        Page<Autorizacao> pagina = new PageImpl<>(autorizacoes, PageRequest.of(0, 20), 1);
        
        when(pixAutoRepository.findByIdUnicoContaContratante(
                eq(idUnicoContaContratante),
                any(Pageable.class)))
                .thenReturn(pagina);

        // Act
        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado = 
            listarAutorizacoesService.listar(idUnicoContaContratante, null, 0, 20, null);

        // Assert
        assertNotNull(resultado);
        AutorizacaoResumidaResponseDto dto = resultado.getConteudo().get(0);
        assertEquals(autorizacao1.getIdAutorizacao().getIdAutorizacao(), dto.getIdAutorizacao());
        assertEquals(autorizacao1.getDataHoraInclusao(), dto.getDataCriacao());
        assertEquals(autorizacao1.getDataInicioVigencia(), dto.getDataInicioVigencia());
        assertEquals(autorizacao1.getDataFimVigencia(), dto.getDataFimVigencia());
        assertEquals(autorizacao1.getIdPessoaRecebedora(), dto.getIdPessoaRecebedora());
        assertEquals(autorizacao1.getValorAutorizacao(), dto.getValor());
        // status (código 1) deve ser traduzido para o nome do enum
        assertEquals("RECEBIDA", dto.getStatus());
    }

    @Test
    @DisplayName("Deve retornar o status como nome do enum para cada item da página")
    void testStatusRetornadoPorItem() {
        // Arrange: autorizacao1 = status 1 (RECEBIDA), autorizacao2 = status 4 (ATIVA)
        List<Autorizacao> autorizacoes = Arrays.asList(autorizacao1, autorizacao2);
        Page<Autorizacao> pagina = new PageImpl<>(autorizacoes, PageRequest.of(0, 20), 2);

        when(pixAutoRepository.findByIdUnicoContaContratante(
                eq(idUnicoContaContratante),
                any(Pageable.class)))
                .thenReturn(pagina);

        // Act
        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado =
            listarAutorizacoesService.listar(idUnicoContaContratante, null, 0, 20, null);

        // Assert: cada item reflete seu próprio status
        assertEquals(2, resultado.getConteudo().size());
        assertEquals("RECEBIDA", resultado.getConteudo().get(0).getStatus());
        assertEquals("ATIVA", resultado.getConteudo().get(1).getStatus());
    }
}
