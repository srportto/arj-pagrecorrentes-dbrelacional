package br.com.srportto.autorizacaostatusproducer.application.eventos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Espelho do payload publicado pelo arj-contratocommand: linha da tabela
 * {@code autorizacoes}, chaves iguais aos nomes das colunas.
 *
 * <p>{@code ignoreUnknown = true}: propriedade nova ainda não replicada aqui é ignorada,
 * não descarta a mensagem inteira (design.md de rede-seguranca-contrato-evento, D3).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
}
