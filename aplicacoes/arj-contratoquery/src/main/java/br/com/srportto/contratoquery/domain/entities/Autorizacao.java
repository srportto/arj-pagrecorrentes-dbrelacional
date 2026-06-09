package br.com.srportto.contratoquery.domain.entities;

import br.com.srportto.contratoquery.domain.converters.TipoProdutoConverter;
import br.com.srportto.contratoquery.domain.enums.TipoProduto;
import br.com.srportto.contratoquery.domain.utilities.IdContaUUIDPartitionDistributor;
import br.com.srportto.contratoquery.domain.utilities.ReversibleUUIDv7;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "autorizacoes")
public class Autorizacao {

    @EmbeddedId
    private IdAutorizacao idAutorizacao;

    @Column(name = "data_fim_vigencia", nullable = false)
    private LocalDate dataFimVigencia;

    @Column(name = "tipo_produto", nullable = false)
    @Convert(converter = TipoProdutoConverter.class)
    private TipoProduto tipoProduto;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "motivo_status", nullable = false)
    private String motivoStatus;

    @Column(name = "data_inicio_vigencia", nullable = false)
    private LocalDate dataInicioVigencia;

    @Column(name = "data_hora_inclusao", nullable = false)
    private LocalDateTime dataHoraInclusao;

    @Column(name = "data_hora_ultima_atlz", nullable = false)
    private LocalDateTime dataHoraUltimaAtualizacao;

    @Column(name = "valor", nullable = false, precision = 17, scale = 2)
    private BigDecimal valorAutorizacao;

    @Column(name = "id_autorizacao_empresa", nullable = false, unique = false)
    private String idAutorizacaoEmpresa;

    @Column(name = "valor_limite", nullable = false, precision = 17, scale = 2)
    private BigDecimal valorLimite;

    @Column(name = "frequencia", nullable = false)
    private short frequenciaPagamento;

    @Column(name = "quantidade_dividas_ciclo", nullable = false)
    private short quantidadeDividasCiclo;

    @Column(name = "indicador_uso_limite_conta", nullable = false)
    private short indicadorUsoLimiteConta;

    @Column(name = "indicador_tipo_mensageria ", nullable = false)
    private short indicadorTipoMensageria;

    @Column(name = "codigo_canal_contratacao", nullable = false)
    private String codigoCanalContratacao;

    @Column(name = "descricao", nullable = true)
    private String descricao;

    @Column(name = "id_unico_conta_contratante", nullable = false, unique = false, length = 36)
    private UUID idUnicoContaContratante;

    @Column(name = "id_pessoa_pagadora", nullable = false, unique = false, length = 36)
    private UUID idPessoaPagadora;

    @Column(name = "id_pessoa_devedora", nullable = false, unique = false, length = 36)
    private UUID idPessoaDevedora;

    @Column(name = "id_pessoa_recebedora", nullable = false, unique = false, length = 36)
    private UUID idPessoaRecebedora;

    @Embedded
    private Cancelamento cancelamento;

    @Column(name = "metadados", nullable = false, unique = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadados;

    public Autorizacao inicializaCriacao(Autorizacao autorizacao) {
        var idUnicoContaContratante = autorizacao.getIdUnicoContaContratante();
        var idParticaoConta = IdContaUUIDPartitionDistributor.getPartitionFast(idUnicoContaContratante);
        var idAutorizacao = ReversibleUUIDv7.generate(idParticaoConta);
        var dataHoraCorrente = LocalDateTime.now();
        var dataCorrente = LocalDate.now();

        autorizacao.setIdAutorizacao(new IdAutorizacao());
        autorizacao.getIdAutorizacao().setIdAutorizacao(idAutorizacao);
        autorizacao.getIdAutorizacao().setIdParticaoConta(idParticaoConta);

        autorizacao.setStatus(1);
        autorizacao.setMotivoStatus("Autorizacao criada com sucesso");
        autorizacao.setDataInicioVigencia(dataCorrente);
        autorizacao.setDataHoraInclusao(dataHoraCorrente);
        autorizacao.setDataHoraUltimaAtualizacao(dataHoraCorrente);
        autorizacao.setIndicadorTipoMensageria((short) 0);

        if (autorizacao.getDataFimVigencia() == null) {
            autorizacao.setDataFimVigencia(LocalDate.of(9999, 12, 31));
        }

        return autorizacao;
    }
}
