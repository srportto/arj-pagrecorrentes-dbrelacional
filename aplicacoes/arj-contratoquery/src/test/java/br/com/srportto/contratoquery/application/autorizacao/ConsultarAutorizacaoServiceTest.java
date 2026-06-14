package br.com.srportto.contratoquery.application.autorizacao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.entities.Autorizacao;
import br.com.srportto.contratoquery.domain.entities.IdAutorizacao;
import br.com.srportto.contratoquery.domain.enums.TipoProduto;
import br.com.srportto.contratoquery.domain.utilities.ReversibleUUIDv7;
import br.com.srportto.contratoquery.entrypoint.contratosrest.AutorizacaoDetalheResponseDto;
import br.com.srportto.contratoquery.shared.exceptions.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do ConsultarAutorizacaoService")
class ConsultarAutorizacaoServiceTest {

    private static final int PARTICAO_VALIDA = 500;
    private static final int PARTICAO_INVALIDA = 950;

    @Mock
    private AutorizacaoQueryRepository autorizacaoQueryRepository;

    @InjectMocks
    private ConsultarAutorizacaoService consultarAutorizacaoService;

    private UUID idAutorizacaoValido;

    @BeforeEach
    void setUp() {
        idAutorizacaoValido = ReversibleUUIDv7.generate(PARTICAO_VALIDA);
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
    @DisplayName("Deve retornar o DTO de detalhe quando a autorização existe")
    void deveRetornarDetalheQuandoExiste() {
        Autorizacao autorizacao = criarAutorizacao(idAutorizacaoValido, PARTICAO_VALIDA);
        when(autorizacaoQueryRepository.findById(any(IdAutorizacao.class)))
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

        verify(autorizacaoQueryRepository, times(1)).findById(any(IdAutorizacao.class));
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException quando a autorização não existe")
    void deveLancarQuandoNaoEncontrada() {
        when(autorizacaoQueryRepository.findById(any(IdAutorizacao.class)))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                consultarAutorizacaoService.consultarPorId(idAutorizacaoValido));
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException quando a partição está fora da faixa 900-999")
    void deveLancarQuandoParticaoForaDaFaixa() {
        UUID idParticaoInvalida = ReversibleUUIDv7.generate(PARTICAO_INVALIDA);

        assertThrows(ResourceNotFoundException.class, () ->
                consultarAutorizacaoService.consultarPorId(idParticaoInvalida));

        verify(autorizacaoQueryRepository, never()).findById(any(IdAutorizacao.class));
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException quando o UUID não é versão 7")
    void deveLancarQuandoUuidNaoEhV7() {
        UUID uuidV4 = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class, () ->
                consultarAutorizacaoService.consultarPorId(uuidV4));

        verify(autorizacaoQueryRepository, never()).findById(any(IdAutorizacao.class));
    }
}
