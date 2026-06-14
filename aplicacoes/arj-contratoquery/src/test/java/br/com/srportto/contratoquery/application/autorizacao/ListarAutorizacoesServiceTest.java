package br.com.srportto.contratoquery.application.autorizacao;

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

import br.com.srportto.contratoquery.domain.entities.Autorizacao;
import br.com.srportto.contratoquery.domain.entities.IdAutorizacao;
import br.com.srportto.contratoquery.entrypoint.contratosrest.AutorizacaoResumidaResponseDto;
import br.com.srportto.contratoquery.entrypoint.contratosrest.PaginacaoResponseDto;
import br.com.srportto.contratoquery.shared.exceptions.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do ListarAutorizacoesService")
class ListarAutorizacoesServiceTest {

    @Mock
    private AutorizacaoQueryRepository autorizacaoQueryRepository;

    @InjectMocks
    private ListarAutorizacoesService listarAutorizacoesService;

    private UUID idUnicoContaContratante;
    private Autorizacao autorizacao1;
    private Autorizacao autorizacao2;

    @BeforeEach
    void setUp() {
        idUnicoContaContratante = UUID.randomUUID();
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
    @DisplayName("Deve retornar erro quando idUnicoContaContratante é nulo")
    void testListarComIdUnicoContaNulo() {
        assertThrows(BusinessException.class, () ->
                listarAutorizacoesService.listar(null, null, 0, 20, null));
    }

    @Test
    @DisplayName("Deve listar todas as autorizações sem filtro de status")
    void testListarSemFiltroStatus() {
        List<Autorizacao> autorizacoes = Arrays.asList(autorizacao1, autorizacao2);
        Page<Autorizacao> pagina = new PageImpl<>(autorizacoes, PageRequest.of(0, 20), 2);

        when(autorizacaoQueryRepository.findByIdUnicoContaContratante(
                eq(idUnicoContaContratante), any(Pageable.class)))
                .thenReturn(pagina);

        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado =
                listarAutorizacoesService.listar(idUnicoContaContratante, null, 0, 20, null);

        assertNotNull(resultado);
        assertEquals(2, resultado.getConteudo().size());
        assertEquals(0, resultado.getPaginaAtual());
        assertEquals(1, resultado.getTotalPaginas());
        assertEquals(2L, resultado.getTotalElementos());
        assertEquals(20, resultado.getTamanho());

        verify(autorizacaoQueryRepository, times(1)).findByIdUnicoContaContratante(
                eq(idUnicoContaContratante), any(Pageable.class));
    }

    @Test
    @DisplayName("Deve listar autorizações com filtro de status")
    void testListarComFiltroStatus() {
        List<Autorizacao> autorizacoes = Arrays.asList(autorizacao1);
        Page<Autorizacao> pagina = new PageImpl<>(autorizacoes, PageRequest.of(0, 20), 1);

        when(autorizacaoQueryRepository.findByIdUnicoContaContratanteAndStatusIn(
                eq(idUnicoContaContratante), eq(Arrays.asList(1)), any(Pageable.class)))
                .thenReturn(pagina);

        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado =
                listarAutorizacoesService.listar(idUnicoContaContratante, Arrays.asList("RECEBIDA"), 0, 20, null);

        assertNotNull(resultado);
        assertEquals(1, resultado.getConteudo().size());
        assertEquals(0, resultado.getPaginaAtual());

        verify(autorizacaoQueryRepository, times(1)).findByIdUnicoContaContratanteAndStatusIn(
                eq(idUnicoContaContratante), eq(Arrays.asList(1)), any(Pageable.class));
    }

    @Test
    @DisplayName("Deve retornar erro quando status é inválido")
    void testListarComStatusInvalido() {
        assertThrows(BusinessException.class, () ->
                listarAutorizacoesService.listar(idUnicoContaContratante, Arrays.asList("STATUS_INVALIDO"), 0, 20, null));
    }

    @Test
    @DisplayName("Deve aplicar valores padrão de paginação")
    void testListarComValoresPadrao() {
        List<Autorizacao> autorizacoes = Arrays.asList(autorizacao1);
        Page<Autorizacao> pagina = new PageImpl<>(autorizacoes, PageRequest.of(0, 20), 1);

        when(autorizacaoQueryRepository.findByIdUnicoContaContratante(
                eq(idUnicoContaContratante), any(Pageable.class)))
                .thenReturn(pagina);

        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado =
                listarAutorizacoesService.listar(idUnicoContaContratante, null, null, null, null);

        assertNotNull(resultado);
        assertEquals(0, resultado.getPaginaAtual());
        assertEquals(20, resultado.getTamanho());
    }

    @Test
    @DisplayName("Deve retornar lista vazia sem erro quando nenhuma autorização encontrada")
    void testListarSemResultados() {
        Page<Autorizacao> paginaVazia = new PageImpl<>(Arrays.asList(), PageRequest.of(0, 20), 0);

        when(autorizacaoQueryRepository.findByIdUnicoContaContratante(
                eq(idUnicoContaContratante), any(Pageable.class)))
                .thenReturn(paginaVazia);

        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado =
                listarAutorizacoesService.listar(idUnicoContaContratante, null, 0, 20, null);

        assertNotNull(resultado);
        assertEquals(0, resultado.getConteudo().size());
        assertEquals(0L, resultado.getTotalElementos());
    }

    @Test
    @DisplayName("Deve suportar múltiplos status no filtro")
    void testListarComMultiplosStatus() {
        List<Autorizacao> autorizacoes = Arrays.asList(autorizacao1, autorizacao2);
        Page<Autorizacao> pagina = new PageImpl<>(autorizacoes, PageRequest.of(0, 20), 2);

        when(autorizacaoQueryRepository.findByIdUnicoContaContratanteAndStatusIn(
                eq(idUnicoContaContratante), eq(Arrays.asList(1, 4)), any(Pageable.class)))
                .thenReturn(pagina);

        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado =
                listarAutorizacoesService.listar(idUnicoContaContratante, Arrays.asList("RECEBIDA", "ATIVA"), 0, 20, null);

        assertNotNull(resultado);
        assertEquals(2, resultado.getConteudo().size());
    }

    @Test
    @DisplayName("Deve converter autorização para DTO resumido corretamente")
    void testConversaoParaDtoResumido() {
        List<Autorizacao> autorizacoes = Arrays.asList(autorizacao1);
        Page<Autorizacao> pagina = new PageImpl<>(autorizacoes, PageRequest.of(0, 20), 1);

        when(autorizacaoQueryRepository.findByIdUnicoContaContratante(
                eq(idUnicoContaContratante), any(Pageable.class)))
                .thenReturn(pagina);

        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado =
                listarAutorizacoesService.listar(idUnicoContaContratante, null, 0, 20, null);

        assertNotNull(resultado);
        AutorizacaoResumidaResponseDto dto = resultado.getConteudo().get(0);
        assertEquals(autorizacao1.getIdAutorizacao().getIdAutorizacao(), dto.getIdAutorizacao());
        assertEquals(autorizacao1.getDataHoraInclusao(), dto.getDataCriacao());
        assertEquals(autorizacao1.getDataInicioVigencia(), dto.getDataInicioVigencia());
        assertEquals(autorizacao1.getDataFimVigencia(), dto.getDataFimVigencia());
        assertEquals(autorizacao1.getIdPessoaRecebedora(), dto.getIdPessoaRecebedora());
        assertEquals(autorizacao1.getValorAutorizacao(), dto.getValor());
        assertEquals("RECEBIDA", dto.getStatus());
    }

    @Test
    @DisplayName("Deve retornar o status como nome do enum para cada item da página")
    void testStatusRetornadoPorItem() {
        List<Autorizacao> autorizacoes = Arrays.asList(autorizacao1, autorizacao2);
        Page<Autorizacao> pagina = new PageImpl<>(autorizacoes, PageRequest.of(0, 20), 2);

        when(autorizacaoQueryRepository.findByIdUnicoContaContratante(
                eq(idUnicoContaContratante), any(Pageable.class)))
                .thenReturn(pagina);

        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado =
                listarAutorizacoesService.listar(idUnicoContaContratante, null, 0, 20, null);

        assertEquals(2, resultado.getConteudo().size());
        assertEquals("RECEBIDA", resultado.getConteudo().get(0).getStatus());
        assertEquals("ATIVA", resultado.getConteudo().get(1).getStatus());
    }

    @Test
    @DisplayName("Deve mapear todos os campos de ordenação e tolerar direção inválida")
    void testOrdenacaoCobreMapeamentoDeCampos() {
        Page<Autorizacao> pagina = new PageImpl<>(Arrays.asList(autorizacao1), PageRequest.of(0, 20), 1);
        when(autorizacaoQueryRepository.findByIdUnicoContaContratante(
                eq(idUnicoContaContratante), any(Pageable.class)))
                .thenReturn(pagina);

        List<String> ordenacoes = Arrays.asList(
                "dataCriacao,asc",
                "valor,desc",
                "idAutorizacao,asc",
                "dataInicioVigencia",
                "dataFimVigencia,desc",
                "idPessoaRecebedora,asc",
                "campoDesconhecido,direcaoInvalida");

        for (String ordenarPor : ordenacoes) {
            PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado =
                    listarAutorizacoesService.listar(idUnicoContaContratante, null, 0, 20, ordenarPor);
            assertNotNull(resultado);
        }
    }
}
