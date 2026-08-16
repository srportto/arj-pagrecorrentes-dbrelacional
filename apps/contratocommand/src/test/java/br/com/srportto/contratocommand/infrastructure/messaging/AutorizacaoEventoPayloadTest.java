package br.com.srportto.contratocommand.infrastructure.messaging;

import br.com.srportto.contratocommand.domain.model.Autorizacao;
import br.com.srportto.contratocommand.domain.model.Cancelamento;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Testes do AutorizacaoEventoPayload.from")
class AutorizacaoEventoPayloadTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Autorizacao autorizacaoCompleta() {
        UUID id = UUID.randomUUID();
        Autorizacao aut = new Autorizacao();
        aut.setIdAutorizacao(id);
        aut.setIdParticaoConta(950);
        aut.setDataFimVigencia(LocalDate.of(2026, 12, 31));
        aut.setTipoProduto(TipoProduto.PIX_AUTO);
        aut.setStatus(4);
        aut.setMotivoStatus("RECEPCAO_SPI_J1");
        aut.setDataInicioVigencia(LocalDate.of(2026, 1, 1));
        aut.setDataHoraInclusao(LocalDateTime.of(2026, 1, 1, 10, 0));
        aut.setDataHoraUltimaAtualizacao(LocalDateTime.of(2026, 1, 2, 11, 0));
        aut.setValorAutorizacao(new BigDecimal("1000.00"));
        aut.setIdAutorizacaoEmpresa("EMP001");
        aut.setValorLimite(new BigDecimal("2000.00"));
        aut.setFrequenciaPagamento((short) 2);
        aut.setQuantidadeDividasCiclo((short) 2);
        aut.setIndicadorUsoLimiteConta((short) 0);
        aut.setIndicadorTipoMensageria((short) 0);
        aut.setCodigoCanalContratacao("C1");
        aut.setDescricao("descricao de teste");
        aut.setIdUnicoContaContratante(UUID.randomUUID());
        aut.setIdPessoaPagadora(UUID.randomUUID());
        aut.setIdPessoaDevedora(UUID.randomUUID());
        aut.setIdPessoaRecebedora(UUID.randomUUID());
        aut.setMetadados("{\"origem\":\"MOBILE\"}");
        return aut;
    }

    @Test
    @DisplayName("mapeia todos os campos da linha, incluindo id_autorizacao e id_particao_conta")
    void mapeiaCamposBasicos() {
        Autorizacao aut = autorizacaoCompleta();

        AutorizacaoEventoPayload payload = AutorizacaoEventoPayload.from(aut);

        assertEquals(aut.getIdAutorizacao(), payload.idAutorizacao());
        assertEquals(aut.getIdParticaoConta(), payload.idParticaoConta());
        assertEquals(TipoProduto.PIX_AUTO.getTipoProduto(), payload.tipoProduto());
        assertEquals(aut.getStatus(), payload.status());
        assertEquals(aut.getIdAutorizacaoEmpresa(), payload.idAutorizacaoEmpresa());
        assertTrue(payload.metadados().has("origem"));
    }

    @Test
    @DisplayName("sem cancelamento: colunas de cancelamento vêm nulas")
    void semCancelamento() {
        AutorizacaoEventoPayload payload = AutorizacaoEventoPayload.from(autorizacaoCompleta());

        assertNull(payload.codigoCanalCancelamento());
        assertNull(payload.idPessoaCancelamento());
        assertNull(payload.dataHoraCancelamento());
        assertNull(payload.motivoCancelamento());
    }

    @Test
    @DisplayName("com cancelamento: colunas de cancelamento são preenchidas")
    void comCancelamento() {
        Autorizacao aut = autorizacaoCompleta();
        Cancelamento cancelamento = new Cancelamento();
        cancelamento.setCodigoCanalCancelamento("C1");
        cancelamento.setIdPessoaCancelamento(UUID.randomUUID());
        cancelamento.setDataHoraCancelamento(LocalDateTime.of(2026, 2, 1, 9, 0));
        cancelamento.setMotivoCancelamento("SOLICITACAO_CLIENTE");
        aut.setCancelamento(cancelamento);
        aut.setStatus(5);

        AutorizacaoEventoPayload payload = AutorizacaoEventoPayload.from(aut);

        assertEquals("C1", payload.codigoCanalCancelamento());
        assertEquals(cancelamento.getIdPessoaCancelamento(), payload.idPessoaCancelamento());
        assertEquals(cancelamento.getDataHoraCancelamento(), payload.dataHoraCancelamento());
        assertEquals("SOLICITACAO_CLIENTE", payload.motivoCancelamento());
    }

    @Test
    @DisplayName("metadados nulo ou vazio vira objeto JSON vazio")
    void metadadosNuloOuVazio() {
        Autorizacao aut = autorizacaoCompleta();
        aut.setMetadados(null);
        assertTrue(AutorizacaoEventoPayload.from(aut).metadados().isObject());

        aut.setMetadados("   ");
        assertTrue(AutorizacaoEventoPayload.from(aut).metadados().isObject());
    }

    @Test
    @DisplayName("chaves do JSON publicado são os nomes das colunas (snake_case), não os campos Java")
    void chavesSaoNomesDeColunas() {
        AutorizacaoEventoPayload payload = AutorizacaoEventoPayload.from(autorizacaoCompleta());

        var json = MAPPER.readTree(MAPPER.writeValueAsString(payload));

        assertTrue(json.has("id_autorizacao"));
        assertTrue(json.has("id_particao_conta"));
        assertTrue(json.has("data_fim_vigencia"));
        assertTrue(json.has("tipo_produto"));
        assertTrue(json.has("motivo_status"));
        assertTrue(json.has("data_hora_ultima_atlz"));
        assertTrue(json.has("valor"));
        assertTrue(json.has("id_autorizacao_empresa"));
        assertTrue(json.has("valor_limite"));
        assertTrue(json.has("codigo_canal_contratacao"));
        assertTrue(json.has("id_unico_conta_contratante"));

        assertFalse(json.has("idAutorizacao"));
        assertFalse(json.has("valorAutorizacao"));
        assertFalse(json.has("dataHoraUltimaAtualizacao"));
    }
}
