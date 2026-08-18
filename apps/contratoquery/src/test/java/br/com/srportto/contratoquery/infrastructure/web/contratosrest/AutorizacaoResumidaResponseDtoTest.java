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
import br.com.srportto.contratoquery.domain.model.Autorizacao;

@DisplayName("Testes do AutorizacaoResumidaResponseDto.from")
class AutorizacaoResumidaResponseDtoTest {

    private Autorizacao base(Integer status, String metadados) {
        return Autorizacao.builder()
                .idAutorizacao(UUID.randomUUID())
                .status(status == null ? null : StatusAutorizacao.obterStatusEnumPorIdStatus(status))
                .dataHoraInclusao(LocalDateTime.now())
                .dataInicioVigencia(LocalDate.now())
                .dataFimVigencia(LocalDate.now().plusDays(30))
                .valorAutorizacao(BigDecimal.TEN)
                .idPessoaRecebedora(UUID.randomUUID())
                .motivoStatus("LEITURA_QRC_J2")
                .metadados(metadados)
                .build();
    }

    @Test
    @DisplayName("mapeia campos, status para nome do enum e metadado JSON")
    void mapeiaCompleto() {
        Autorizacao a = base(4, "{\"origem\":\"MOBILE\"}");

        AutorizacaoResumidaResponseDto dto = AutorizacaoResumidaResponseDto.from(a);

        assertEquals(a.getIdAutorizacao(), dto.idAutorizacao());
        assertEquals(a.getDataHoraInclusao(), dto.dataCriacao());
        assertEquals(a.getValorAutorizacao(), dto.valor());
        assertEquals("ATIVA", dto.status());
        assertEquals("LEITURA_QRC_J2", dto.motivoStatus());
        assertNotNull(dto.metadado());
        assertTrue(dto.metadado().has("origem"));
        assertNull(dto.nomeRecebedor());
    }

    @Test
    @DisplayName("metadado nulo resulta em metadado nulo no DTO")
    void metadadoNulo() {
        AutorizacaoResumidaResponseDto dto = AutorizacaoResumidaResponseDto.from(base(1, null));
        assertNull(dto.metadado());
        assertEquals("RECEBIDA", dto.status());
    }

    @Test
    @DisplayName("metadado com JSON inválido é tratado como nulo (sem lançar)")
    void metadadoInvalido() {
        AutorizacaoResumidaResponseDto dto = AutorizacaoResumidaResponseDto.from(base(1, "{invalido"));
        assertNull(dto.metadado());
    }

    @Test
    @DisplayName("status nulo resulta em status nulo no DTO")
    void statusNulo() {
        AutorizacaoResumidaResponseDto dto = AutorizacaoResumidaResponseDto.from(base(null, null));
        assertNull(dto.status());
    }
}
