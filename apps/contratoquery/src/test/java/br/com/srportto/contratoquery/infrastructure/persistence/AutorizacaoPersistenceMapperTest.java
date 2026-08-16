package br.com.srportto.contratoquery.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratoquery.domain.enums.TipoProduto;
import br.com.srportto.contratoquery.domain.model.Autorizacao;

import static org.junit.jupiter.api.Assertions.*;

/** Cobre todos os campos, inclusive metadados (jsonb), cancelamento embutido e os enums convertidos. */
@DisplayName("Testes do AutorizacaoPersistenceMapper")
class AutorizacaoPersistenceMapperTest {

    private final AutorizacaoPersistenceMapper mapper = new AutorizacaoPersistenceMapper();

    @Test
    @DisplayName("paraDominio mapeia todos os campos, inclusive cancelamento embutido")
    void paraDominio_MapeiaTodosOsCampos() {
        UUID idAutorizacao = UUID.randomUUID();
        UUID idUnicoContaContratante = UUID.randomUUID();
        UUID idPessoaPagadora = UUID.randomUUID();
        UUID idPessoaDevedora = UUID.randomUUID();
        UUID idPessoaRecebedora = UUID.randomUUID();
        UUID idPessoaCancelamento = UUID.randomUUID();
        LocalDateTime agora = LocalDateTime.now();
        LocalDate hoje = LocalDate.now();

        AutorizacaoJpaEntity entity = new AutorizacaoJpaEntity();
        entity.setIdAutorizacao(new IdAutorizacaoJpaEmbeddable(idAutorizacao, 5));
        entity.setDataFimVigencia(hoje.plusDays(30));
        entity.setTipoProduto(TipoProduto.PIX_AUTO);
        entity.setTipoJornada(TipoJornadaAutorizacao.SPI_J1);
        entity.setStatus(5);
        entity.setMotivoStatus("CANCELADA_PELO_PAGADOR");
        entity.setDataInicioVigencia(hoje);
        entity.setDataHoraInclusao(agora);
        entity.setDataHoraUltimaAtualizacao(agora);
        entity.setValorAutorizacao(new BigDecimal("123.45"));
        entity.setIdAutorizacaoEmpresa("EMP001");
        entity.setValorLimite(new BigDecimal("999.00"));
        entity.setFrequenciaPagamento((short) 2);
        entity.setQuantidadeDividasCiclo((short) 3);
        entity.setIndicadorUsoLimiteConta((short) 1);
        entity.setIndicadorTipoMensageria((short) 1);
        entity.setCodigoCanalContratacao("C1");
        entity.setDescricao("descricao de teste");
        entity.setIdUnicoContaContratante(idUnicoContaContratante);
        entity.setIdPessoaPagadora(idPessoaPagadora);
        entity.setIdPessoaDevedora(idPessoaDevedora);
        entity.setIdPessoaRecebedora(idPessoaRecebedora);
        entity.setCancelamento(new CancelamentoJpaEmbeddable(
                "C2", idPessoaCancelamento, agora, "CANCELADA_PELO_PAGADOR"));
        entity.setMetadados("{\"origem\":\"MOBILE\"}");
        entity.setVersion(3L);

        Autorizacao dominio = mapper.paraDominio(entity);

        assertEquals(idAutorizacao, dominio.getIdAutorizacao());
        assertEquals(TipoProduto.PIX_AUTO, dominio.getTipoProduto());
        assertEquals(TipoJornadaAutorizacao.SPI_J1, dominio.getTipoJornada());
        assertEquals(5, dominio.getStatus());
        assertEquals("CANCELADA_PELO_PAGADOR", dominio.getMotivoStatus());
        assertEquals(hoje, dominio.getDataInicioVigencia());
        assertEquals(hoje.plusDays(30), dominio.getDataFimVigencia());
        assertEquals(agora, dominio.getDataHoraInclusao());
        assertEquals(agora, dominio.getDataHoraUltimaAtualizacao());
        assertEquals(new BigDecimal("123.45"), dominio.getValorAutorizacao());
        assertEquals("EMP001", dominio.getIdAutorizacaoEmpresa());
        assertEquals(new BigDecimal("999.00"), dominio.getValorLimite());
        assertEquals(2, dominio.getFrequenciaPagamento());
        assertEquals(3, dominio.getQuantidadeDividasCiclo());
        assertEquals(1, dominio.getIndicadorUsoLimiteConta());
        assertEquals(1, dominio.getIndicadorTipoMensageria());
        assertEquals("C1", dominio.getCodigoCanalContratacao());
        assertEquals("descricao de teste", dominio.getDescricao());
        assertEquals(idUnicoContaContratante, dominio.getIdUnicoContaContratante());
        assertEquals(idPessoaPagadora, dominio.getIdPessoaPagadora());
        assertEquals(idPessoaDevedora, dominio.getIdPessoaDevedora());
        assertEquals(idPessoaRecebedora, dominio.getIdPessoaRecebedora());
        assertEquals("{\"origem\":\"MOBILE\"}", dominio.getMetadados());

        assertNotNull(dominio.getCancelamento());
        assertEquals("C2", dominio.getCancelamento().getCodigoCanalCancelamento());
        assertEquals(idPessoaCancelamento, dominio.getCancelamento().getIdPessoaCancelamento());
        assertEquals(agora, dominio.getCancelamento().getDataHoraCancelamento());
        assertEquals("CANCELADA_PELO_PAGADOR", dominio.getCancelamento().getMotivoCancelamento());
    }

    @Test
    @DisplayName("paraDominio tolera cancelamento nulo (autorização nunca cancelada)")
    void paraDominio_CancelamentoNulo() {
        AutorizacaoJpaEntity entity = new AutorizacaoJpaEntity();
        entity.setIdAutorizacao(new IdAutorizacaoJpaEmbeddable(UUID.randomUUID(), 5));
        entity.setTipoProduto(TipoProduto.DDA_AUTO);
        entity.setTipoJornada(TipoJornadaAutorizacao.DESCONHECIDA);
        entity.setStatus(4);
        entity.setMotivoStatus("Teste");
        entity.setDataInicioVigencia(LocalDate.now());
        entity.setDataFimVigencia(LocalDate.now().plusDays(1));
        entity.setDataHoraInclusao(LocalDateTime.now());
        entity.setDataHoraUltimaAtualizacao(LocalDateTime.now());
        entity.setValorAutorizacao(BigDecimal.TEN);
        entity.setIdAutorizacaoEmpresa("EMP002");
        entity.setValorLimite(BigDecimal.TEN);
        entity.setIdUnicoContaContratante(UUID.randomUUID());
        entity.setIdPessoaPagadora(UUID.randomUUID());
        entity.setIdPessoaDevedora(UUID.randomUUID());
        entity.setIdPessoaRecebedora(UUID.randomUUID());
        entity.setMetadados("{}");
        entity.setCancelamento(null);

        Autorizacao dominio = mapper.paraDominio(entity);

        assertNull(dominio.getCancelamento());
    }
}
