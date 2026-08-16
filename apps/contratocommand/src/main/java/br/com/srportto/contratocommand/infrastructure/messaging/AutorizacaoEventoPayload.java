package br.com.srportto.contratocommand.infrastructure.messaging;

import br.com.srportto.contratocommand.domain.model.Autorizacao;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representacao exata da linha da tabela {@code autorizacoes}: as chaves do JSON
 * publicado sao os nomes das colunas, nao os nomes dos campos Java da entidade.
 */
public record AutorizacaoEventoPayload(

        @JsonProperty("id_autorizacao") UUID idAutorizacao,

        @JsonProperty("id_particao_conta") Integer idParticaoConta,

        @JsonProperty("data_fim_vigencia") LocalDate dataFimVigencia,

        @JsonProperty("tipo_produto") Long tipoProduto,

        @JsonProperty("tipo_jornada") Long tipoJornada,

        @JsonProperty("status") Integer status,

        @JsonProperty("motivo_status") String motivoStatus,

        @JsonProperty("data_inicio_vigencia") LocalDate dataInicioVigencia,

        @JsonProperty("data_hora_inclusao") LocalDateTime dataHoraInclusao,

        @JsonProperty("data_hora_ultima_atlz") LocalDateTime dataHoraUltimaAtualizacao,

        @JsonProperty("valor") BigDecimal valorAutorizacao,

        @JsonProperty("id_autorizacao_empresa") String idAutorizacaoEmpresa,

        @JsonProperty("valor_limite") BigDecimal valorLimite,

        @JsonProperty("frequencia") Short frequenciaPagamento,

        @JsonProperty("quantidade_dividas_ciclo") Short quantidadeDividasCiclo,

        @JsonProperty("indicador_uso_limite_conta") Short indicadorUsoLimiteConta,

        @JsonProperty("indicador_tipo_mensageria") Short indicadorTipoMensageria,

        @JsonProperty("codigo_canal_contratacao") String codigoCanalContratacao,

        @JsonProperty("descricao") String descricao,

        @JsonProperty("id_unico_conta_contratante") UUID idUnicoContaContratante,

        @JsonProperty("id_pessoa_pagadora") UUID idPessoaPagadora,

        @JsonProperty("id_pessoa_devedora") UUID idPessoaDevedora,

        @JsonProperty("id_pessoa_recebedora") UUID idPessoaRecebedora,

        @JsonProperty("codigo_canal_cancelamento") String codigoCanalCancelamento,

        @JsonProperty("id_pessoa_cancelamento") UUID idPessoaCancelamento,

        @JsonProperty("data_hora_cancelamento") LocalDateTime dataHoraCancelamento,

        @JsonProperty("motivo_cancelamento") String motivoCancelamento,

        @JsonProperty("metadados") JsonNode metadados) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static AutorizacaoEventoPayload from(Autorizacao autorizacao) {
        var tipoProduto = autorizacao.getTipoProduto();
        var tipoJornada = autorizacao.getTipoJornada();
        var cancelamento = autorizacao.getCancelamento();

        return new AutorizacaoEventoPayload(
                autorizacao.getIdAutorizacao(),
                autorizacao.getIdParticaoConta(),
                autorizacao.getDataFimVigencia(),
                tipoProduto != null ? tipoProduto.getTipoProduto() : null,
                tipoJornada != null ? tipoJornada.getCodigoJornada() : null,
                autorizacao.getStatus(),
                autorizacao.getMotivoStatus(),
                autorizacao.getDataInicioVigencia(),
                autorizacao.getDataHoraInclusao(),
                autorizacao.getDataHoraUltimaAtualizacao(),
                autorizacao.getValorAutorizacao(),
                autorizacao.getIdAutorizacaoEmpresa(),
                autorizacao.getValorLimite(),
                autorizacao.getFrequenciaPagamento(),
                autorizacao.getQuantidadeDividasCiclo(),
                autorizacao.getIndicadorUsoLimiteConta(),
                autorizacao.getIndicadorTipoMensageria(),
                autorizacao.getCodigoCanalContratacao(),
                autorizacao.getDescricao(),
                autorizacao.getIdUnicoContaContratante(),
                autorizacao.getIdPessoaPagadora(),
                autorizacao.getIdPessoaDevedora(),
                autorizacao.getIdPessoaRecebedora(),
                cancelamento != null ? cancelamento.getCodigoCanalCancelamento() : null,
                cancelamento != null ? cancelamento.getIdPessoaCancelamento() : null,
                cancelamento != null ? cancelamento.getDataHoraCancelamento() : null,
                cancelamento != null ? cancelamento.getMotivoCancelamento() : null,
                parseMetadados(autorizacao.getMetadados()));
    }

    private static JsonNode parseMetadados(String metadados) {
        if (metadados == null || metadados.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }

        try {
            return MAPPER.readTree(metadados);
        } catch (Exception e) {
            return JsonNodeFactory.instance.objectNode();
        }
    }

}
