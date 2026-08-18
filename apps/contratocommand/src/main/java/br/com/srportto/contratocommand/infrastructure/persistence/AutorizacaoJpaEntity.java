package br.com.srportto.contratocommand.infrastructure.persistence;

import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
// Unicidade de id_autorizacao_empresa NÃO é declarada aqui: é índice único PARCIAL desde a v1.0.4
// (só partições quentes), forma que JPA não expressa. @UniqueConstraint prometeria garantia
// diferente da real. Ver infra/local/postgres/migrations/v1.0.4.
@Table(name = "autorizacoes") // autorizacoes de produtos financeiros (PIX Automatico, DDA Automatico)
public class AutorizacaoJpaEntity {

    @EmbeddedId
    private IdAutorizacaoJpaEmbeddable idAutorizacao;

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

    // Unicidade NÃO é declarada aqui, pelo mesmo motivo do comentário no topo da classe: é índice
    // único PARCIAL (só partições quentes), e `unique = true` geraria constraint de coluna única
    // sem a chave de particionamento — rejeitada em tabela PARTITION BY, além de prometer uma
    // garantia mais ampla do que a real.
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
    private String codigoCanalContratacao; // C1 - presencial, C2 - digital, C3 - central de atendimento

    @Column(name = "descricao", nullable = true)
    private String descricao;

    @Column(name = "id_unico_conta_contratante", nullable = false, unique = false, length = 36)
    private java.util.UUID idUnicoContaContratante;

    @Column(name = "id_pessoa_pagadora", nullable = false, unique = false, length = 36)
    private java.util.UUID idPessoaPagadora;

    @Column(name = "id_pessoa_devedora", nullable = false, unique = false, length = 36)
    private java.util.UUID idPessoaDevedora;

    @Column(name = "id_pessoa_recebedora", nullable = false, unique = false, length = 36)
    private java.util.UUID idPessoaRecebedora;

    @Embedded
    private CancelamentoJpaEmbeddable cancelamento;

    @Column(name = "metadados", nullable = false, unique = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadados;

}
