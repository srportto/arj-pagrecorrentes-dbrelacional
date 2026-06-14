package br.com.srportto.contratoquery.entrypoint.contratosrest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.entities.Autorizacao;
import br.com.srportto.contratoquery.domain.entities.IdAutorizacao;
import br.com.srportto.contratoquery.domain.enums.TipoProduto;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do AutorizacaoDetalheResponseDto.from")
class AutorizacaoDetalheResponseDtoTest {

    private Autorizacao base(Integer status, String metadados) {
        Autorizacao a = new Autorizacao();
        a.setIdAutorizacao(new IdAutorizacao(UUID.randomUUID(), 10));
        a.setTipoProduto(TipoProduto.PIX_AUTO);
        a.setStatus(status);
        a.setDataInicioVigencia(LocalDate.now());
        a.setDataFimVigencia(LocalDate.now().plusDays(30));
        a.setDataHoraInclusao(LocalDateTime.now());
        a.setDataHoraUltimaAtualizacao(LocalDateTime.now());
        a.setValorAutorizacao(BigDecimal.valueOf(123.45));
        a.setValorLimite(BigDecimal.valueOf(1000));
        a.setIdUnicoContaContratante(UUID.randomUUID());
        a.setIdPessoaPagadora(UUID.randomUUID());
        a.setIdPessoaDevedora(UUID.randomUUID());
        a.setIdPessoaRecebedora(UUID.randomUUID());
        a.setIdAutorizacaoEmpresa("EMP1");
        a.setDescricao("desc");
        a.setMetadados(metadados);
        return a;
    }

    @Test
    @DisplayName("mapeia representação completa com status e metadado")
    void mapeiaCompleto() {
        Autorizacao a = base(4, "{\"k\":\"v\"}");
        AutorizacaoDetalheResponseDto dto = AutorizacaoDetalheResponseDto.from(a);

        assertEquals(a.getIdAutorizacao().getIdAutorizacao(), dto.getIdAutorizacao());
        assertEquals(TipoProduto.PIX_AUTO, dto.getTipoProduto());
        assertEquals("ATIVA", dto.getStatus());
        assertEquals(a.getValorLimite(), dto.getValorLimite());
        assertEquals(a.getIdPessoaPagadora(), dto.getIdPessoaPagadora());
        assertNotNull(dto.getMetadado());
        assertTrue(dto.getMetadado().has("k"));
    }

    @Test
    @DisplayName("metadado nulo e status nulo são tolerados")
    void nulos() {
        AutorizacaoDetalheResponseDto dto = AutorizacaoDetalheResponseDto.from(base(null, null));
        assertNull(dto.getMetadado());
        assertNull(dto.getStatus());
    }

    @Test
    @DisplayName("metadado com JSON inválido vira nulo sem lançar")
    void metadadoInvalido() {
        AutorizacaoDetalheResponseDto dto = AutorizacaoDetalheResponseDto.from(base(1, "{quebrado"));
        assertNull(dto.getMetadado());
        assertEquals("RECEBIDA", dto.getStatus());
    }
}
