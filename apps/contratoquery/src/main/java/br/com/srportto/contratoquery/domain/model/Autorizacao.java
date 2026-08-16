package br.com.srportto.contratoquery.domain.model;

import br.com.srportto.contratoquery.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratoquery.domain.enums.TipoProduto;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modelo de domínio de uma autorização — Java puro, sem nenhuma anotação de ORM (D1). Esta app é
 * somente leitura, então o modelo é imutável (classe {@code @Value}, sem setter — não record: 24
 * campos deixariam um record de acessores posicionais desconfortável, e o getter-style
 * ({@code getIdAutorizacao()}) mantém uniformidade com o resto do monorepo).
 *
 * Não carrega {@code idParticaoConta} (D4 — extração de partição é detalhe de armazenamento, mora
 * só no adaptador) nem {@code version} (D2 — controle de concorrência não interessa a quem só lê).
 * A entidade JPA correspondente é {@code infrastructure/persistence/AutorizacaoJpaEntity}; a ponte
 * entre as duas é {@code AutorizacaoPersistenceMapper}.
 */
@Value
@Builder
public class Autorizacao {

    UUID idAutorizacao;
    TipoProduto tipoProduto;
    TipoJornadaAutorizacao tipoJornada;
    Integer status;
    String motivoStatus;
    LocalDate dataInicioVigencia;
    LocalDate dataFimVigencia;
    LocalDateTime dataHoraInclusao;
    LocalDateTime dataHoraUltimaAtualizacao;
    BigDecimal valorAutorizacao;
    String idAutorizacaoEmpresa;
    BigDecimal valorLimite;
    short frequenciaPagamento;
    short quantidadeDividasCiclo;
    short indicadorUsoLimiteConta;
    short indicadorTipoMensageria;
    String codigoCanalContratacao;
    String descricao;
    UUID idUnicoContaContratante;
    UUID idPessoaPagadora;
    UUID idPessoaDevedora;
    UUID idPessoaRecebedora;
    Cancelamento cancelamento;
    String metadados;
}
