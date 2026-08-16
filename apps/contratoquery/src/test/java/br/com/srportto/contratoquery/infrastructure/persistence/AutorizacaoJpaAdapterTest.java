package br.com.srportto.contratoquery.infrastructure.persistence;

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

import br.com.srportto.contratoquery.domain.enums.TipoProduto;
import br.com.srportto.contratoquery.domain.exception.ApplicationException;
import br.com.srportto.contratoquery.domain.model.Autorizacao;

/**
 * Cobre a cascata de localização em partições (D3) — mudou de camada, saiu de
 * {@code ConsultarAutorizacaoService} e entrou aqui. Mesmos cenários de sempre, agora mockando
 * {@link SpringDataAutorizacaoRepository} e usando o mapper real (é uma classe simples, sem
 * necessidade de mock) para conferir que o retorno já vem em domínio puro.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do AutorizacaoJpaAdapter — cascata de localização")
class AutorizacaoJpaAdapterTest {

    private static final int PARTICAO_VALIDA = 500;
    private static final int PARTICAO_FORA_DA_FAIXA_DE_IDS = 950;
    private static final int PARTICAO_EXPURGO = 953;

    @Mock
    private SpringDataAutorizacaoRepository springDataRepository;

    private AutorizacaoJpaAdapter adapter;

    private UUID idAutorizacaoValido;

    @BeforeEach
    void setUp() {
        idAutorizacaoValido = ReversibleUUIDv7.generate(PARTICAO_VALIDA);
        adapter = adapterComNivel3(true);
    }

    private AutorizacaoJpaAdapter adapterComNivel3(boolean habilitado) {
        return new AutorizacaoJpaAdapter(springDataRepository, new AutorizacaoPersistenceMapper(), habilitado);
    }

    private AutorizacaoJpaEntity criarEntidade(UUID idAutorizacao, int idParticaoConta) {
        AutorizacaoJpaEntity auto = new AutorizacaoJpaEntity();
        auto.setIdAutorizacao(new IdAutorizacaoJpaEmbeddable(idAutorizacao, idParticaoConta));
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
        AutorizacaoJpaEntity entidade = criarEntidade(idAutorizacaoValido, PARTICAO_VALIDA);
        when(springDataRepository.findById(any(IdAutorizacaoJpaEmbeddable.class)))
                .thenReturn(Optional.of(entidade));

        Optional<Autorizacao> resultado = adapter.buscarPorId(idAutorizacaoValido);

        assertTrue(resultado.isPresent());
        assertEquals(idAutorizacaoValido, resultado.get().getIdAutorizacao());

        verify(springDataRepository, times(1)).findById(any(IdAutorizacaoJpaEmbeddable.class));
        verify(springDataRepository, never()).buscarNaFaixaDeExpurgo(any(), anyInt());
        verify(springDataRepository, never()).buscarEmOutrasParticoesQuentes(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Nivel 2: encontra na faixa de expurgo e nao aciona o nivel 3")
    void nivel2_NaoAcionaNivel3() {
        when(springDataRepository.findById(any(IdAutorizacaoJpaEmbeddable.class))).thenReturn(Optional.empty());
        when(springDataRepository.buscarNaFaixaDeExpurgo(any(), anyInt()))
                .thenReturn(List.of(criarEntidade(idAutorizacaoValido, PARTICAO_EXPURGO)));

        Optional<Autorizacao> resultado = adapter.buscarPorId(idAutorizacaoValido);

        assertTrue(resultado.isPresent());
        verify(springDataRepository, never()).buscarEmOutrasParticoesQuentes(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Nivel 3 desabilitado: nao consulta as demais particoes quentes antes do 404")
    void nivel3Desabilitado_EncerraCascataMaisCedo() {
        AutorizacaoJpaAdapter adapterSemNivel3 = adapterComNivel3(false);
        when(springDataRepository.findById(any(IdAutorizacaoJpaEmbeddable.class))).thenReturn(Optional.empty());
        when(springDataRepository.buscarNaFaixaDeExpurgo(any(), anyInt())).thenReturn(List.of());

        Optional<Autorizacao> resultado = adapterSemNivel3.buscarPorId(idAutorizacaoValido);

        assertTrue(resultado.isEmpty());
        verify(springDataRepository, never()).buscarEmOutrasParticoesQuentes(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Nivel 3 habilitado: consulta as demais particoes quentes antes de desistir")
    void nivel3Habilitado_EsgotaCascataAntesDo404() {
        when(springDataRepository.findById(any(IdAutorizacaoJpaEmbeddable.class))).thenReturn(Optional.empty());
        when(springDataRepository.buscarNaFaixaDeExpurgo(any(), anyInt())).thenReturn(List.of());
        when(springDataRepository.buscarEmOutrasParticoesQuentes(any(), anyInt(), anyInt())).thenReturn(List.of());

        Optional<Autorizacao> resultado = adapter.buscarPorId(idAutorizacaoValido);

        assertTrue(resultado.isEmpty());
        verify(springDataRepository, times(1)).buscarEmOutrasParticoesQuentes(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Duplicidade entre particoes vira erro de aplicacao, sem escolher uma linha")
    void duplicidade_NaoDesempata() {
        when(springDataRepository.findById(any(IdAutorizacaoJpaEmbeddable.class))).thenReturn(Optional.empty());
        when(springDataRepository.buscarNaFaixaDeExpurgo(any(), anyInt())).thenReturn(List.of(
                criarEntidade(idAutorizacaoValido, PARTICAO_EXPURGO),
                criarEntidade(idAutorizacaoValido, PARTICAO_EXPURGO + 1)));

        assertThrows(ApplicationException.class, () -> adapter.buscarPorId(idAutorizacaoValido));

        verify(springDataRepository, never()).buscarEmOutrasParticoesQuentes(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Particao fora da faixa: nao consulta o banco")
    void particaoForaDaFaixa_NaoConsultaBanco() {
        UUID idParticaoInvalida = ReversibleUUIDv7.generate(PARTICAO_FORA_DA_FAIXA_DE_IDS);

        Optional<Autorizacao> resultado = adapter.buscarPorId(idParticaoInvalida);

        assertTrue(resultado.isEmpty());
        verify(springDataRepository, never()).findById(any(IdAutorizacaoJpaEmbeddable.class));
        verify(springDataRepository, never()).buscarNaFaixaDeExpurgo(any(), anyInt());
        verify(springDataRepository, never()).buscarEmOutrasParticoesQuentes(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("UUID que nao e versao 7: nao consulta o banco")
    void uuidNaoEhV7_NaoConsultaBanco() {
        UUID uuidV4 = UUID.randomUUID();

        Optional<Autorizacao> resultado = adapter.buscarPorId(uuidV4);

        assertTrue(resultado.isEmpty());
        verify(springDataRepository, never()).findById(any(IdAutorizacaoJpaEmbeddable.class));
    }
}
