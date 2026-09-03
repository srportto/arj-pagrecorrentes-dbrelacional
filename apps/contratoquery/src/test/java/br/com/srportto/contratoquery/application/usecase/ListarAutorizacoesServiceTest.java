package br.com.srportto.contratoquery.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.enums.CampoOrdenacao;
import br.com.srportto.contratoquery.domain.enums.DirecaoOrdenacao;
import br.com.srportto.contratoquery.domain.enums.StatusAutorizacao;
import br.com.srportto.contratoquery.domain.exception.BusinessException;
import br.com.srportto.contratoquery.domain.model.Autorizacao;
import br.com.srportto.contratoquery.domain.model.Ordenacao;
import br.com.srportto.contratoquery.domain.model.PaginaAutorizacoes;
import br.com.srportto.contratoquery.domain.port.in.ListarAutorizacoesUseCase.ResultadoListagem;
import br.com.srportto.contratoquery.domain.port.out.AutorizacaoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do ListarAutorizacoesService")
class ListarAutorizacoesServiceTest {

    @Mock
    private AutorizacaoRepository repository;

    private ListarAutorizacoesService service;

    private UUID idUnicoContaContratante;
    private Autorizacao autorizacao1;
    private Autorizacao autorizacao2;

    @BeforeEach
    void setUp() {
        service = new ListarAutorizacoesService(repository);
        idUnicoContaContratante = UUID.randomUUID();
        autorizacao1 = criarAutorizacao(1, 100.00);
        autorizacao2 = criarAutorizacao(4, 500.00);
    }

    private Autorizacao criarAutorizacao(int status, double valor) {
        return Autorizacao.builder()
                .idAutorizacao(UUID.randomUUID())
                .status(StatusAutorizacao.obterStatusEnumPorIdStatus(status))
                .dataHoraInclusao(LocalDateTime.now())
                .dataInicioVigencia(LocalDate.now())
                .dataFimVigencia(LocalDate.now().plusDays(30))
                .valorAutorizacao(BigDecimal.valueOf(valor))
                .idUnicoContaContratante(idUnicoContaContratante)
                .idPessoaRecebedora(UUID.randomUUID())
                .motivoStatus("Teste")
                .frequenciaPagamento((short) 1)
                .quantidadeDividasCiclo((short) 1)
                .indicadorUsoLimiteConta((short) 0)
                .indicadorTipoMensageria((short) 0)
                .codigoCanalContratacao("01")
                .idAutorizacaoEmpresa("EMP001")
                .valorLimite(BigDecimal.valueOf(10000))
                .build();
    }

    @Test
    @DisplayName("Deve retornar erro quando idUnicoContaContratante é nulo")
    void testListarComIdUnicoContaNulo() {
        assertThrows(BusinessException.class, () ->
                service.listar(null, null, 0, 20, null));
    }

    @Test
    @DisplayName("Deve listar todas as autorizações sem filtro de status")
    void testListarSemFiltroStatus() {
        when(repository.listarPorConta(eq(idUnicoContaContratante), isNull(), eq(0), eq(20), any(Ordenacao.class)))
                .thenReturn(new PaginaAutorizacoes(Arrays.asList(autorizacao1, autorizacao2), 2));

        ResultadoListagem resultado = service.listar(idUnicoContaContratante, null, 0, 20, null);

        assertNotNull(resultado);
        assertEquals(2, resultado.conteudo().size());
        assertEquals(0, resultado.paginaAtual());
        assertEquals(1, resultado.totalPaginas());
        assertEquals(2L, resultado.totalElementos());
        assertEquals(20, resultado.tamanho());

        verify(repository, times(1)).listarPorConta(
                eq(idUnicoContaContratante), isNull(), eq(0), eq(20), any(Ordenacao.class));
    }

    @Test
    @DisplayName("Deve listar autorizações com filtro de status")
    void testListarComFiltroStatus() {
        when(repository.listarPorConta(eq(idUnicoContaContratante), eq(List.of(StatusAutorizacao.RECEBIDA)), eq(0), eq(20), any(Ordenacao.class)))
                .thenReturn(new PaginaAutorizacoes(List.of(autorizacao1), 1));

        ResultadoListagem resultado = service.listar(idUnicoContaContratante, List.of("RECEBIDA"), 0, 20, null);

        assertNotNull(resultado);
        assertEquals(1, resultado.conteudo().size());
        assertEquals(0, resultado.paginaAtual());

        verify(repository, times(1)).listarPorConta(
                eq(idUnicoContaContratante), eq(List.of(StatusAutorizacao.RECEBIDA)), eq(0), eq(20), any(Ordenacao.class));
    }

    @Test
    @DisplayName("Deve retornar erro quando status é inválido")
    void testListarComStatusInvalido() {
        assertThrows(BusinessException.class, () ->
                service.listar(idUnicoContaContratante, List.of("STATUS_INVALIDO"), 0, 20, null));
    }

    @Test
    @DisplayName("Deve aplicar valores padrão de paginação")
    void testListarComValoresPadrao() {
        when(repository.listarPorConta(eq(idUnicoContaContratante), isNull(), eq(0), eq(20), any(Ordenacao.class)))
                .thenReturn(new PaginaAutorizacoes(List.of(autorizacao1), 1));

        ResultadoListagem resultado = service.listar(idUnicoContaContratante, null, null, null, null);

        assertNotNull(resultado);
        assertEquals(0, resultado.paginaAtual());
        assertEquals(20, resultado.tamanho());
    }

    @Test
    @DisplayName("Deve retornar lista vazia sem erro quando nenhuma autorização encontrada")
    void testListarSemResultados() {
        when(repository.listarPorConta(eq(idUnicoContaContratante), isNull(), eq(0), eq(20), any(Ordenacao.class)))
                .thenReturn(new PaginaAutorizacoes(List.of(), 0));

        ResultadoListagem resultado = service.listar(idUnicoContaContratante, null, 0, 20, null);

        assertNotNull(resultado);
        assertEquals(0, resultado.conteudo().size());
        assertEquals(0L, resultado.totalElementos());
    }

    @Test
    @DisplayName("Deve suportar múltiplos status no filtro")
    void testListarComMultiplosStatus() {
        when(repository.listarPorConta(eq(idUnicoContaContratante), eq(List.of(StatusAutorizacao.RECEBIDA, StatusAutorizacao.ATIVA)), eq(0), eq(20), any(Ordenacao.class)))
                .thenReturn(new PaginaAutorizacoes(Arrays.asList(autorizacao1, autorizacao2), 2));

        ResultadoListagem resultado = service.listar(idUnicoContaContratante, List.of("RECEBIDA", "ATIVA"), 0, 20, null);

        assertNotNull(resultado);
        assertEquals(2, resultado.conteudo().size());
    }

    @Test
    @DisplayName("Deve repassar o campo de ordenação mapeado e a direção corretas para a porta")
    void testOrdenacaoRepassaCampoEDirecaoParaAPorta() {
        Ordenacao esperado = new Ordenacao(CampoOrdenacao.VALOR, DirecaoOrdenacao.ASC);
        when(repository.listarPorConta(eq(idUnicoContaContratante), isNull(), eq(0), eq(20), eq(esperado)))
                .thenReturn(new PaginaAutorizacoes(List.of(autorizacao1), 1));

        ResultadoListagem resultado = service.listar(idUnicoContaContratante, null, 0, 20, "valor,asc");

        assertNotNull(resultado);
        verify(repository).listarPorConta(
                eq(idUnicoContaContratante), isNull(), eq(0), eq(20), eq(esperado));
    }

    @Test
    @DisplayName("Deve mapear todos os campos de ordenação válidos")
    void testOrdenacaoCobreMapeamentoDeCampos() {
        when(repository.listarPorConta(eq(idUnicoContaContratante), isNull(), eq(0), eq(20), any(Ordenacao.class)))
                .thenReturn(new PaginaAutorizacoes(List.of(autorizacao1), 1));

        List<String> ordenacoes = Arrays.asList(
                "dataCriacao,asc",
                "valor,desc",
                "idAutorizacao,asc",
                "dataInicioVigencia",
                "dataFimVigencia,desc",
                "idPessoaRecebedora,asc",
                "dataHoraInclusao,desc",
                "status,asc");

        for (String ordenarPor : ordenacoes) {
            ResultadoListagem resultado = service.listar(idUnicoContaContratante, null, 0, 20, ordenarPor);
            assertNotNull(resultado);
        }
    }

    @Test
    @DisplayName("Deve rejeitar campos de ordenação desconhecidos com erro de negócio")
    void testOrdenacaoComCampoDesconhecido() {
        assertThrows(BusinessException.class, () ->
                service.listar(idUnicoContaContratante, null, 0, 20, "campoDesconhecido,asc"));
    }

    @Test
    @DisplayName("Deve rejeitar direção de ordenação desconhecida sem consultar o repositório")
    void testOrdenacaoComDirecaoDesconhecida() {
        assertThrows(BusinessException.class, () ->
                service.listar(idUnicoContaContratante, null, 0, 20, "valor,ascc"));
        assertThrows(BusinessException.class, () ->
                service.listar(idUnicoContaContratante, null, 0, 20, "valor,ASCENDING"));

        verify(repository, never()).listarPorConta(any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("Deve rejeitar expressão de ordenação malformada (campo vazio, direção vazia ou excedente)")
    void testOrdenacaoComExpressaoMalformada() {
        assertThrows(BusinessException.class, () ->
                service.listar(idUnicoContaContratante, null, 0, 20, "valor,"));
        assertThrows(BusinessException.class, () ->
                service.listar(idUnicoContaContratante, null, 0, 20, ",asc"));
        assertThrows(BusinessException.class, () ->
                service.listar(idUnicoContaContratante, null, 0, 20, "valor,asc,extra"));

        verify(repository, never()).listarPorConta(any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("Deve preservar o comportamento atual para expressões válidas de regressão")
    void testOrdenacaoRegressaoComportamentoAtual() {
        when(repository.listarPorConta(any(), any(), anyInt(), anyInt(), any(Ordenacao.class)))
                .thenReturn(new PaginaAutorizacoes(List.of(autorizacao1), 1));

        service.listar(idUnicoContaContratante, null, 0, 20, "valor,asc");
        verify(repository).listarPorConta(eq(idUnicoContaContratante), isNull(), eq(0), eq(20),
                eq(new Ordenacao(CampoOrdenacao.VALOR, DirecaoOrdenacao.ASC)));

        service.listar(idUnicoContaContratante, null, 0, 20, "valor");
        verify(repository).listarPorConta(eq(idUnicoContaContratante), isNull(), eq(0), eq(20),
                eq(new Ordenacao(CampoOrdenacao.VALOR, DirecaoOrdenacao.DESC)));

        service.listar(idUnicoContaContratante, null, 0, 20, "dataHoraInclusao,desc");
        service.listar(idUnicoContaContratante, null, 0, 20, null);
        // "dataHoraInclusao,desc" e ordenarPor ausente resolvem para a mesma Ordenacao (D4/D5) — 2 chamadas.
        verify(repository, times(2)).listarPorConta(eq(idUnicoContaContratante), isNull(), eq(0), eq(20),
                eq(new Ordenacao(CampoOrdenacao.DATA_CRIACAO, DirecaoOrdenacao.DESC)));
        assertEquals(new Ordenacao(CampoOrdenacao.DATA_CRIACAO, DirecaoOrdenacao.DESC), Ordenacao.padrao());
    }

    @Test
    @DisplayName("Deve rejeitar tamanho acima do teto com erro de negócio")
    void testTamanhoPaginaAcimaDoTeto() {
        assertThrows(BusinessException.class, () ->
                service.listar(idUnicoContaContratante, null, 0, 101, null));
    }

    @Test
    @DisplayName("Deve rejeitar página negativa com erro de negócio")
    void testPaginaNegativa() {
        assertThrows(BusinessException.class, () ->
                service.listar(idUnicoContaContratante, null, -1, 20, null));
    }

    @Test
    @DisplayName("Deve rejeitar tamanho zero com erro de negócio")
    void testTamanhoZero() {
        assertThrows(BusinessException.class, () ->
                service.listar(idUnicoContaContratante, null, 0, 0, null));
    }

    @Test
    @DisplayName("Deve rejeitar tamanho negativo com erro de negócio")
    void testTamanhoNegativo() {
        assertThrows(BusinessException.class, () ->
                service.listar(idUnicoContaContratante, null, 0, -1, null));
    }

    @Test
    @DisplayName("Deve aceitar tamanho no limite máximo (100)")
    void testTamanhoNoTeto() {
        when(repository.listarPorConta(eq(idUnicoContaContratante), isNull(), eq(0), eq(100), any(Ordenacao.class)))
                .thenReturn(new PaginaAutorizacoes(List.of(autorizacao1), 1));

        ResultadoListagem resultado = service.listar(idUnicoContaContratante, null, 0, 100, null);

        assertNotNull(resultado);
        assertEquals(100, resultado.tamanho());
    }
}
