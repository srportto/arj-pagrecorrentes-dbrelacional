package br.com.srportto.contratocommand.domain.entities;

import br.com.srportto.contratocommand.domain.converters.TipoJornadaAutorizacaoConverter;
import br.com.srportto.contratocommand.domain.converters.TipoProdutoConverter;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.utilities.IdContaUUIDPartitionDistributor;
import br.com.srportto.contratocommand.domain.utilities.ReversibleUUIDv7;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
// A unicidade de id_autorizacao_empresa NÃO é declarada aqui: desde a migration v1.0.4 ela é um
// índice único PARCIAL (`WHERE id_particao_conta < 900`, só as partições quentes), forma que JPA
// não sabe expressar. Declará-la como @UniqueConstraint prometeria uma garantia diferente da que o
// banco impõe — e, com ddl-auto: none, seria só documentação errada. Ver
// infra/local/postgres/migrations/v1.0.4 para o racional.
@Table(name = "autorizacoes") // autorizacoes de produtos financeiros (PIX Automatico, DDA Automatico)
public class Autorizacao {

    @EmbeddedId
    private IdAutorizacao idAutorizacao;

    @Column(name = "data_fim_vigencia", nullable = false)
    private LocalDate dataFimVigencia;

    @Column(name = "tipo_produto", nullable = false)
    @Convert(converter = TipoProdutoConverter.class)
    private TipoProduto tipoProduto;

    @Column(name = "tipo_jornada", nullable = false)
    @Convert(converter = TipoJornadaAutorizacaoConverter.class)
    private TipoJornadaAutorizacao tipoJornada;

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

    // Unicidade real: constraint composta (id_particao_conta, id_autorizacao_empresa), declarada
    // em @Table acima — não `unique = true` aqui, que geraria constraint de coluna única sem a
    // chave de particionamento, rejeitada pelo Postgres em tabela PARTITION BY.
    @Column(name = "id_autorizacao_empresa", nullable = false)
    private String idAutorizacaoEmpresa;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "valor_limite", nullable = false, precision = 17, scale = 2)
    private BigDecimal valorLimite;

    @Column(name = "frequencia", nullable = false)
    private short frequenciaPagamento; // frequencia de pagamento na faixa 1..4 (ver @Min(1)@Max(4) no request)

    @Column(name = "quantidade_dividas_ciclo", nullable = false)
    private short quantidadeDividasCiclo;

    @Column(name = "indicador_uso_limite_conta", nullable = false)
    private short indicadorUsoLimiteConta; // 0 - nao utiliza limite de conta, 1 - utiliza limite de conta

    @Column(name = "indicador_tipo_mensageria", nullable = false)
    private short indicadorTipoMensageria; // 0 - nao utiliza mensageria, 1 - utiliza mensageria SPI , 2 ...

    @Column(name = "codigo_canal_contratacao", nullable = false)
    private String codigoCanalContratacao; // C1 - canal presencial, C2 - canal digital, C3 - canal central de
                                           // atendimento

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

    /** Status com que cada produto nasce na criação — fonte da verdade de {@link #inicializaCriacao()}. */
    private static final Map<TipoProduto, StatusAutorizacao> STATUS_INICIAL_POR_PRODUTO = new EnumMap<>(TipoProduto.class);

    static {
        STATUS_INICIAL_POR_PRODUTO.put(TipoProduto.PIX_AUTO, StatusAutorizacao.RECEBIDA);
        STATUS_INICIAL_POR_PRODUTO.put(TipoProduto.DDA_AUTO, StatusAutorizacao.ATIVA);
    }

    /**
     * Inicializa esta autorização para criação: gera a chave composta (UUID + partição embutida),
     * marca o status inicial conforme o produto (fonte da verdade: {@link #STATUS_INICIAL_POR_PRODUTO})
     * e aplica os defaults de datas/indicadores. O {@code motivoStatus} é responsabilidade do mapper
     * (derivado de {@code MotivoStatusAutorizacao} conforme a jornada), não deste método.
     */
    public Autorizacao inicializaCriacao() {

        var idParticaoConta = IdContaUUIDPartitionDistributor.getPartitionFast(this.idUnicoContaContratante);
        var idAutorizacaoGerado = ReversibleUUIDv7.generate(idParticaoConta);
        var dataHoraCorrente = LocalDateTime.now();
        var dataCorrente = LocalDate.now();

        this.idAutorizacao = new IdAutorizacao();
        this.idAutorizacao.setIdAutorizacao(idAutorizacaoGerado);
        this.idAutorizacao.setIdParticaoConta(idParticaoConta);

        var statusInicial = STATUS_INICIAL_POR_PRODUTO.get(this.tipoProduto);
        if (statusInicial == null) {
            throw new IllegalStateException(
                    "Nenhum status inicial de criação definido para o produto " + this.tipoProduto);
        }
        this.status = (int) statusInicial.getStatusAutorizacao();
        this.dataInicioVigencia = dataCorrente;
        this.dataHoraInclusao = dataHoraCorrente;
        this.dataHoraUltimaAtualizacao = dataHoraCorrente;
        this.indicadorTipoMensageria = (short) 0;

        if (this.dataFimVigencia == null) {
            this.dataFimVigencia = LocalDate.of(9999, 12, 31);
        }

        return this;
    }

}
