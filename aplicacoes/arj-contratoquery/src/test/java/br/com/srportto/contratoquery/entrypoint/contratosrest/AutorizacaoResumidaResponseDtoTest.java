package br.com.srportto.contratoquery.entrypoint.contratosrest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratoquery.domain.entities.Autorizacao;
import br.com.srportto.contratoquery.domain.entities.IdAutorizacao;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do AutorizacaoResumidaResponseDto.from")
class AutorizacaoResumidaResponseDtoTest {

    private Autorizacao base(Integer status, String metadados) {
        Autorizacao a = new Autorizacao();
        a.setIdAutorizacao(new IdAutorizacao(UUID.randomUUID(), 10));
        a.setStatus(status);
        a.setDataHoraInclusao(LocalDateTime.now());
        a.setDataInicioVigencia(LocalDate.now());
        a.setDataFimVigencia(LocalDate.now().plusDays(30));
        a.setValorAutorizacao(BigDecimal.TEN);
        a.setIdPessoaRecebedora(UUID.randomUUID());
        a.setMetadados(metadados);
        return a;
    }

    @Test
    @DisplayName("mapeia campos, status para nome do enum e metadado JSON")
    void mapeiaCompleto() {
        Autorizacao a = base(4, "{\"origem\":\"MOBILE\"}");

        AutorizacaoResumidaResponseDto dto = AutorizacaoResumidaResponseDto.from(a);

        assertEquals(a.getIdAutorizacao().getIdAutorizacao(), dto.getIdAutorizacao());
        assertEquals(a.getDataHoraInclusao(), dto.getDataCriacao());
        assertEquals(a.getValorAutorizacao(), dto.getValor());
        assertEquals("ATIVA", dto.getStatus());
        assertNotNull(dto.getMetadado());
        assertTrue(dto.getMetadado().has("origem"));
        assertNull(dto.getNomeRecebedor());
    }

    @Test
    @DisplayName("metadado nulo resulta em metadado nulo no DTO")
    void metadadoNulo() {
        AutorizacaoResumidaResponseDto dto = AutorizacaoResumidaResponseDto.from(base(1, null));
        assertNull(dto.getMetadado());
        assertEquals("RECEBIDA", dto.getStatus());
    }

    @Test
    @DisplayName("metadado com JSON inválido é tratado como nulo (sem lançar)")
    void metadadoInvalido() {
        AutorizacaoResumidaResponseDto dto = AutorizacaoResumidaResponseDto.from(base(1, "{invalido"));
        assertNull(dto.getMetadado());
    }

    @Test
    @DisplayName("status nulo resulta em status nulo no DTO")
    void statusNulo() {
        AutorizacaoResumidaResponseDto dto = AutorizacaoResumidaResponseDto.from(base(null, null));
        assertNull(dto.getStatus());
    }
}
