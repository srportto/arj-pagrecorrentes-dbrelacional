package br.com.srportto.contratoquery.application.autorizacao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.entities.Autorizacao;
import br.com.srportto.contratoquery.domain.entities.IdAutorizacao;
import br.com.srportto.contratoquery.domain.enums.TipoProduto;
import br.com.srportto.contratoquery.domain.utilities.ReversibleUUIDv7;
import br.com.srportto.contratoquery.entrypoint.contratosrest.AutorizacaoDetalheResponseDto;
import br.com.srportto.contratoquery.shared.exceptions.ApplicationException;
import br.com.srportto.contratoquery.shared.exceptions.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do ConsultarAutorizacaoService")
class ConsultarAutorizacaoServiceTest {

    private static final int PARTICAO_VALIDA = 500;
    private static final int PARTICAO_FORA_DA_FAIXA_DE_IDS = 950;
    private static final int PARTICAO_EXPURGO = 953;

    @Mock
    private AutorizacaoRepository repository;

    private ConsultarAutorizacaoService consultarAutorizacaoService;

    private UUID idAutorizacaoValido;

    @BeforeEach
    void setUp() {
        idAutorizacaoValido = ReversibleUUIDv7.generate(PARTICAO_VALIDA);
        consultarAutorizacaoService = servicoComNivel3(true);
    }

    private ConsultarAutorizacaoService servicoComNivel3(boolean habilitado) {
        return new ConsultarAutorizacaoService(repository, habilitado);
    }

    private Autorizacao criarAutorizacao(UUID idAutorizacao, int idParticaoConta) {
        Autorizacao auto = new Autorizacao();
        auto.setIdAutorizacao(new IdAutorizacao(idAutorizacao, idParticaoConta));
        auto.setTipoProduto(TipoProduto.PIX_AUTO);
        auto.setStatus(4);
        auto.setMotivoStatus("Teste");
        auto.setDataInicioVigencia(LocalDate.now());
        auto.setDataFimVigencia(LocalDate.now().plusDays(30));
        auto.setDataHoraInclusao(LocalDateTime.now());
        auto.setDataHoraUltimaAtualizacao(LocalDateTime.now());
        auto.setValorAutorizacao(BigDecimal.valueOf(500.00));
        auto.setValorLimite(BigDecimal.valueOf(10000));
        auto.setIdUnicoContaContratante(UUID.randomUUID());
        auto.setIdPessoaPagadora(UUID.randomUUID());
        auto.setIdPessoaDevedora(UUID.randomUUID());
        auto.setIdPessoaRecebedora(UUID.randomUUID());
        auto.setIdAutorizacaoEmpresa("EMP001");
        auto.setCodigoCanalContratacao("01");
        auto.setFrequenciaPagamento((short) 1);
        auto.setQuantidadeDividasCiclo((short) 1);
        auto.setIndicadorUsoLimiteConta((short) 0);
        auto.setIndicadorTipoMensageria((short) 0);
        auto.setDescricao("Autorizacao de teste");
        auto.setMetadados("{\"origem\":\"MOBILE\"}");
        return auto;
    }

    @Test
    @DisplayName("Nivel 1: encontra na particao do id e nao aciona os niveis seguintes")
    void nivel1_NaoAcionaNiveisSeguintes() {
        Autorizacao autorizacao = criarAutorizacao(idAutorizacaoValido, PARTICAO_VALIDA);
        when(repository.findById(any(IdAutorizacao.class)))
                .thenReturn(Optional.of(autorizacao));

        AutorizacaoDetalheResponseDto resultado =
                consultarAutorizacaoService.consultarPorId(idAutorizacaoValido);

        assertNotNull(resultado);
        assertEquals(idAutorizacaoValido, resultado.getIdAutorizacao());
        assertEquals(TipoProduto.PIX_AUTO, resultado.getTipoProduto());
        assertEquals("ATIVA", resultado.getStatus());
        assertEquals(autorizacao.getValorAutorizacao(), resultado.getValor());
        assertEquals(autorizacao.getIdUnicoContaContratante(), resultado.getIdUnicoContaContratante());
        assertNotNull(resultado.getMetadado());
        assertTrue(resultado.getMetadado().has("origem"));

        verify(repository, times(1)).findById(any(IdAutorizacao.class));
        verify(repository, never()).buscarNaFaixaDeExpurgo(any(), anyInt());
        verify(repository, never()).buscarEmOutrasParticoesQuentes(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Nivel 2: encontra na faixa de expurgo e nao aciona o nivel 3")
    void nivel2_NaoAcionaNivel3() {
        when(repository.findById(any(IdAutorizacao.class))).thenReturn(Optional.empty());
        when(repository.buscarNaFaixaDeExpurgo(any(), anyInt()))
                .thenReturn(List.of(criarAutorizacao(idAutorizacaoValido, PARTICAO_EXPURGO)));

        AutorizacaoDetalheResponseDto resultado =
                consultarAutorizacaoService.consultarPorId(idAutorizacaoValido);

        assertNotNull(resultado);
        assertEquals(idAutorizacaoValido, resultado.getIdAutorizacao());
        verify(repository, never()).buscarEmOutrasParticoesQuentes(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Nivel 3 desabilitado: nao consulta as demais particoes quentes antes do 404")
    void nivel3Desabilitado_EncerraCascataMaisCedo() {
        ConsultarAutorizacaoService servico = servicoComNivel3(false);
        when(repository.findById(any(IdAutorizacao.class))).thenReturn(Optional.empty());
        when(repository.buscarNaFaixaDeExpurgo(any(), anyInt())).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> servico.consultarPorId(idAutorizacaoValido));

        verify(repository, never()).buscarEmOutrasParticoesQuentes(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Nivel 3 habilitado: consulta as demais particoes quentes antes de desistir")
    void nivel3Habilitado_EsgotaCascataAntesDo404() {
        when(repository.findById(any(IdAutorizacao.class))).thenReturn(Optional.empty());
        when(repository.buscarNaFaixaDeExpurgo(any(), anyInt())).thenReturn(List.of());
        when(repository.buscarEmOutrasParticoesQuentes(any(), anyInt(), anyInt())).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () ->
                consultarAutorizacaoService.consultarPorId(idAutorizacaoValido));

        verify(repository, times(1)).buscarEmOutrasParticoesQuentes(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Duplicidade entre particoes vira erro de aplicacao, sem escolher uma linha")
    void duplicidade_NaoDesempata() {
        when(repository.findById(any(IdAutorizacao.class))).thenReturn(Optional.empty());
        when(repository.buscarNaFaixaDeExpurgo(any(), anyInt())).thenReturn(List.of(
                criarAutorizacao(idAutorizacaoValido, PARTICAO_EXPURGO),
                criarAutorizacao(idAutorizacaoValido, PARTICAO_EXPURGO + 1)));

        assertThrows(ApplicationException.class, () ->
                consultarAutorizacaoService.consultarPorId(idAutorizacaoValido));

        verify(repository, never()).buscarEmOutrasParticoesQuentes(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException quando a partição do id está fora da faixa de partições quentes")
    void deveLancarQuandoParticaoForaDaFaixa() {
        UUID idParticaoInvalida = ReversibleUUIDv7.generate(PARTICAO_FORA_DA_FAIXA_DE_IDS);

        assertThrows(ResourceNotFoundException.class, () ->
                consultarAutorizacaoService.consultarPorId(idParticaoInvalida));

        verify(repository, never()).findById(any(IdAutorizacao.class));
        verify(repository, never()).buscarNaFaixaDeExpurgo(any(), anyInt());
        verify(repository, never()).buscarEmOutrasParticoesQuentes(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException quando o UUID não é versão 7")
    void deveLancarQuandoUuidNaoEhV7() {
        UUID uuidV4 = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class, () ->
                consultarAutorizacaoService.consultarPorId(uuidV4));

        verify(repository, never()).findById(any(IdAutorizacao.class));
    }
}
