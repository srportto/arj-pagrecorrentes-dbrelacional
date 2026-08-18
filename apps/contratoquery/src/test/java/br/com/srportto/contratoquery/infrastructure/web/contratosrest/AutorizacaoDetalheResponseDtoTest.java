package br.com.srportto.contratoquery.infrastructure.web.contratosrest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.enums.StatusAutorizacao;
import br.com.srportto.contratoquery.domain.enums.TipoProduto;
import br.com.srportto.contratoquery.domain.model.Autorizacao;

@DisplayName("Testes do AutorizacaoDetalheResponseDto.from")
class AutorizacaoDetalheResponseDtoTest {

    private Autorizacao base(Integer status, String metadados) {
        return Autorizacao.builder()
                .idAutorizacao(UUID.randomUUID())
                .tipoProduto(TipoProduto.PIX_AUTO)
                .status(status == null ? null : StatusAutorizacao.obterStatusEnumPorIdStatus(status))
                .dataInicioVigencia(LocalDate.now())
                .dataFimVigencia(LocalDate.now().plusDays(30))
                .dataHoraInclusao(LocalDateTime.now())
                .dataHoraUltimaAtualizacao(LocalDateTime.now())
                .valorAutorizacao(BigDecimal.valueOf(123.45))
                .valorLimite(BigDecimal.valueOf(1000))
                .idUnicoContaContratante(UUID.randomUUID())
                .idPessoaPagadora(UUID.randomUUID())
                .idPessoaDevedora(UUID.randomUUID())
                .idPessoaRecebedora(UUID.randomUUID())
                .idAutorizacaoEmpresa("EMP1")
                .descricao("desc")
                .motivoStatus("RECEPCAO_SPI_J1")
                .metadados(metadados)
                .build();
    }

    @Test
    @DisplayName("mapeia representação completa com status e metadado")
    void mapeiaCompleto() {
        Autorizacao a = base(4, "{\"k\":\"v\"}");
        AutorizacaoDetalheResponseDto dto = AutorizacaoDetalheResponseDto.from(a);

        assertEquals(a.getIdAutorizacao(), dto.idAutorizacao());
        assertEquals(TipoProduto.PIX_AUTO, dto.tipoProduto());
        assertEquals("ATIVA", dto.status());
        assertEquals("RECEPCAO_SPI_J1", dto.motivoStatus());
        assertEquals(a.getValorLimite(), dto.valorLimite());
        assertEquals(a.getIdPessoaPagadora(), dto.idPessoaPagadora());
        assertNotNull(dto.metadado());
        assertTrue(dto.metadado().has("k"));
    }

    @Test
    @DisplayName("metadado nulo e status nulo são tolerados")
    void nulos() {
        AutorizacaoDetalheResponseDto dto = AutorizacaoDetalheResponseDto.from(base(null, null));
        assertNull(dto.metadado());
        assertNull(dto.status());
    }

    @Test
    @DisplayName("metadado com JSON inválido vira nulo sem lançar")
    void metadadoInvalido() {
        AutorizacaoDetalheResponseDto dto = AutorizacaoDetalheResponseDto.from(base(1, "{quebrado"));
        assertNull(dto.metadado());
        assertEquals("RECEBIDA", dto.status());
    }
}
