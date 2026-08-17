package br.com.srportto.contratocommand.domain.model;

import br.com.srportto.contratocommand.domain.enums.MotivoStatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Modelo de domínio de uma autorização de pagamento recorrente. Java puro — nenhuma anotação de
 * ORM. {@code version} trafega como dado opaco de controle de concorrência (não interpretado pelo
 * domínio, só carregado de ida e volta pelo {@code AutorizacaoPersistenceMapper}).
 *
 * {@code idParticaoConta} é, pela mesma razão, um eco opaco da localização física atual da linha —
 * necessário porque o payload do evento publicado no SNS ({@code AutorizacaoEventoPayload}) inclui
 * a coluna {@code id_particao_conta}, e após o expurgo mover a linha essa informação só existe na
 * entidade JPA gerenciada, não é recomputável a partir do UUID (que carrega a partição de
 * *criação*, não a atual). O domínio nunca lê, decide ou calcula esse valor — só o adaptador de
 * persistência o preenche, na volta.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Autorizacao {

    private UUID idAutorizacao;
    private Integer idParticaoConta;
    private LocalDate dataFimVigencia;
    private TipoProduto tipoProduto;
    private TipoJornadaAutorizacao tipoJornada;
    private Integer status;
    private String motivoStatus;
    private LocalDate dataInicioVigencia;
    private LocalDateTime dataHoraInclusao;
    private LocalDateTime dataHoraUltimaAtualizacao;
    private BigDecimal valorAutorizacao;
    private String idAutorizacaoEmpresa;
    private Long version;
    private BigDecimal valorLimite;
    private short frequenciaPagamento;
    private short quantidadeDividasCiclo;
    private short indicadorUsoLimiteConta;
    private short indicadorTipoMensageria;
    private String codigoCanalContratacao;
    private String descricao;
    private UUID idUnicoContaContratante;
    private UUID idPessoaPagadora;
    private UUID idPessoaDevedora;
    private UUID idPessoaRecebedora;
    private Cancelamento cancelamento;
    private String metadados;

    /** Status com que cada produto nasce na criação — fonte da verdade de {@link #inicializaCriacao(UUID)}. */
    private static final Map<TipoProduto, StatusAutorizacao> STATUS_INICIAL_POR_PRODUTO = new EnumMap<>(TipoProduto.class);

    static {
        STATUS_INICIAL_POR_PRODUTO.put(TipoProduto.PIX_AUTO, StatusAutorizacao.RECEBIDA);
        STATUS_INICIAL_POR_PRODUTO.put(TipoProduto.DDA_AUTO, StatusAutorizacao.ATIVA);
    }

    /**
     * Recebe a identidade já gerada (pela porta {@code GeradorIdentidadeAutorizacao}), marca o
     * status inicial por produto ({@link #STATUS_INICIAL_POR_PRODUTO}) e aplica defaults.
     * {@code motivoStatus} é responsabilidade do mapper, não deste método.
     */
    public Autorizacao inicializaCriacao(UUID idGerado) {

        var dataHoraCorrente = LocalDateTime.now();
        var dataCorrente = LocalDate.now();

        this.idAutorizacao = idGerado;

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

    /** Decisão do pagador em RECEBIDA: aprova a jornada 1 do PIX_AUTO. Fixa status+motivo juntos. */
    public void aprovar() {
        this.status = (int) StatusAutorizacao.ATIVA.getStatusAutorizacao();
        this.motivoStatus = MotivoStatusAutorizacao.AUTORIZACAO_ACEITA_POR_TODOS.name();
        this.dataHoraUltimaAtualizacao = LocalDateTime.now();
    }

    /** Decisão do pagador em RECEBIDA: rejeita a jornada 1 do PIX_AUTO. Fixa status+motivo juntos. */
    public void rejeitarPeloPagador() {
        this.status = (int) StatusAutorizacao.REJEITADA.getStatusAutorizacao();
        this.motivoStatus = MotivoStatusAutorizacao.REJEITADA_PAGADOR.name();
        this.dataHoraUltimaAtualizacao = LocalDateTime.now();
    }

    /** Prazo de 10 minutos da jornada 1 vencido sem decisão do pagador. Fixa status+motivo juntos. */
    public void expirarJornada1() {
        this.status = (int) StatusAutorizacao.REJEITADA.getStatusAutorizacao();
        this.motivoStatus = MotivoStatusAutorizacao.REJEITADA_SISTEMA_TIMEOUT_J1.name();
        this.dataHoraUltimaAtualizacao = LocalDateTime.now();
    }

    /** Cancelamento a pedido: fixa status+dados de cancelamento juntos, com o mesmo instante da entrada. */
    public void cancelar(Cancelamento dadosCancelamento) {
        this.status = (int) StatusAutorizacao.CANCELADA.getStatusAutorizacao();
        this.cancelamento = dadosCancelamento;
        this.dataHoraUltimaAtualizacao = dadosCancelamento.getDataHoraCancelamento();
    }

}
