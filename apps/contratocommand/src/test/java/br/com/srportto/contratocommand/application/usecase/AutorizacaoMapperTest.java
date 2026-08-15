package br.com.srportto.contratocommand.application.usecase;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do AutorizacaoMapper (impl gerado)")
class AutorizacaoMapperTest {

    private final AutorizacaoMapper mapper = new AutorizacaoMapperImpl();

    @Test
    @DisplayName("toDomain mapeia campos e o afterMapping aplica produto e inicializaCriacao (PIX)")
    void toDomainPix() {
        Autorizacao aut = mapper.toDomain(TestFixtures.criarContextPix());

        assertNotNull(aut);
        assertEquals(TipoProduto.PIX_AUTO, aut.getTipoProduto());
        assertEquals(0, aut.getValorAutorizacao().compareTo(new BigDecimal("1000.00")));
        assertEquals((short) 2, aut.getFrequenciaPagamento());
        assertNotNull(aut.getIdAutorizacao());
        assertNotNull(aut.getIdAutorizacao().getIdAutorizacao());
        assertEquals(1, aut.getStatus()); // StatusAutorizacao.RECEBIDA
        assertEquals("RECEPCAO_SPI_J1", aut.getMotivoStatus());
        assertEquals(TipoJornadaAutorizacao.SPI_J1, aut.getTipoJornada());
    }

    @Test
    @DisplayName("toDomain mapeia produto DDA pelo nome do comando e grava status ATIVA")
    void toDomainDda() {
        Autorizacao aut = mapper.toDomain(TestFixtures.criarContextDda());

        assertNotNull(aut);
        assertEquals(TipoProduto.DDA_AUTO, aut.getTipoProduto());
        assertEquals(4, aut.getStatus()); // StatusAutorizacao.ATIVA
        assertEquals(TipoJornadaAutorizacao.SPI_J1, aut.getTipoJornada());
    }

    @Test
    @DisplayName("toDomain persiste a jornada recebida mesmo quando difere da jornada usada no motivo (QRC_J3)")
    void toDomainPersisteJornadaQrcJ3() {
        Autorizacao aut = mapper.toDomain(TestFixtures.criarContext(
                "PIX_AUTO", new BigDecimal("1000.00"), LocalDate.now().plusDays(30), null,
                TipoJornadaAutorizacao.QRC_J3));

        assertEquals(TipoJornadaAutorizacao.QRC_J3, aut.getTipoJornada());
        assertEquals("LEITURA_QRC_J3", aut.getMotivoStatus());
    }

    @Test
    @DisplayName("toDomain resolve tipoProduto com case diferente via obterTipoProdutoEnumPorNome (nao usa Enum.valueOf implicito)")
    void toDomainProdutoCaseInsensitivo() {
        Autorizacao aut = mapper.toDomain(TestFixtures.criarContext(
                "pix_auto", new BigDecimal("10"), LocalDate.now().plusDays(1), null,
                TipoJornadaAutorizacao.QRC_J2));

        assertEquals(TipoProduto.PIX_AUTO, aut.getTipoProduto());
    }

    @Test
    @DisplayName("toDomain lança BusinessException (nao IllegalArgumentException) para produto desconhecido")
    void toDomainProdutoDesconhecidoLancaBusinessException() {
        assertThrows(BusinessException.class, () -> mapper.toDomain(TestFixtures.criarContext(
                "CARTAO_CREDITO", new BigDecimal("10"), LocalDate.now().plusDays(1), null,
                TipoJornadaAutorizacao.QRC_J2)));
    }

    @Test
    @DisplayName("toDomain grava metadados quando presentes")
    void toDomainComMetadado() {
        var meta = new ObjectMapper().readTree("{\"k\":\"v\"}");
        Autorizacao aut = mapper.toDomain(TestFixtures.criarContext(
                "PIX_AUTO", new BigDecimal("10"), LocalDate.now().plusDays(1), meta,
                TipoJornadaAutorizacao.QRC_J2));

        assertNotNull(aut.getMetadados());
        assertTrue(aut.getMetadados().contains("k"));
    }
}
