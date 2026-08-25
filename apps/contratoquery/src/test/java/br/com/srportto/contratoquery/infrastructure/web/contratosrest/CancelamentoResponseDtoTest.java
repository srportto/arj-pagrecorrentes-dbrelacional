package br.com.srportto.contratoquery.infrastructure.web.contratosrest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.srportto.contratoquery.domain.model.Cancelamento;

@DisplayName("Testes do CancelamentoResponseDto.from")
class CancelamentoResponseDtoTest {

    @Test
    @DisplayName("mapeia os 4 campos quando Cancelamento não é nulo")
    void mapeiaCampos() {
        var idPessoa = UUID.randomUUID();
        var dataHora = LocalDateTime.now();
        var cancelamento = Cancelamento.builder()
                .codigoCanalCancelamento("01")
                .idPessoaCancelamento(idPessoa)
                .dataHoraCancelamento(dataHora)
                .motivoCancelamento("SOLICITACAO_CLIENTE")
                .build();

        CancelamentoResponseDto dto = CancelamentoResponseDto.from(cancelamento);

        assertEquals("01", dto.codigoCanalCancelamento());
        assertEquals(idPessoa, dto.idPessoaCancelamento());
        assertEquals(dataHora, dto.dataHoraCancelamento());
        assertEquals("SOLICITACAO_CLIENTE", dto.motivoCancelamento());
    }

    @Test
    @DisplayName("retorna null quando Cancelamento é nulo")
    void retornaNuloQuandoCancelamentoNulo() {
        assertNull(CancelamentoResponseDto.from(null));
    }
}
