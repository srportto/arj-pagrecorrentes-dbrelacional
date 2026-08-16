package br.com.srportto.contratoquery.infrastructure.persistence;

import br.com.srportto.contratoquery.domain.model.Autorizacao;
import br.com.srportto.contratoquery.domain.model.Cancelamento;
import org.springframework.stereotype.Component;

/**
 * Conversão de {@link AutorizacaoJpaEntity} para {@link Autorizacao} — sentido único basta, porque
 * esta app não escreve (D1). {@code version} e a partição física não atravessam para o domínio
 * (D2, D4).
 */
@Component
public class AutorizacaoPersistenceMapper {

    public Autorizacao paraDominio(AutorizacaoJpaEntity entity) {
        var idEntity = entity.getIdAutorizacao();
        var cancelamentoEntity = entity.getCancelamento();

        return Autorizacao.builder()
                .idAutorizacao(idEntity.getIdAutorizacao())
                .tipoProduto(entity.getTipoProduto())
                .tipoJornada(entity.getTipoJornada())
                .status(entity.getStatus())
                .motivoStatus(entity.getMotivoStatus())
                .dataInicioVigencia(entity.getDataInicioVigencia())
                .dataFimVigencia(entity.getDataFimVigencia())
                .dataHoraInclusao(entity.getDataHoraInclusao())
                .dataHoraUltimaAtualizacao(entity.getDataHoraUltimaAtualizacao())
                .valorAutorizacao(entity.getValorAutorizacao())
                .idAutorizacaoEmpresa(entity.getIdAutorizacaoEmpresa())
                .valorLimite(entity.getValorLimite())
                .frequenciaPagamento(entity.getFrequenciaPagamento())
                .quantidadeDividasCiclo(entity.getQuantidadeDividasCiclo())
                .indicadorUsoLimiteConta(entity.getIndicadorUsoLimiteConta())
                .indicadorTipoMensageria(entity.getIndicadorTipoMensageria())
                .codigoCanalContratacao(entity.getCodigoCanalContratacao())
                .descricao(entity.getDescricao())
                .idUnicoContaContratante(entity.getIdUnicoContaContratante())
                .idPessoaPagadora(entity.getIdPessoaPagadora())
                .idPessoaDevedora(entity.getIdPessoaDevedora())
                .idPessoaRecebedora(entity.getIdPessoaRecebedora())
                .cancelamento(cancelamentoEntity == null ? null : Cancelamento.builder()
                        .codigoCanalCancelamento(cancelamentoEntity.getCodigoCanalCancelamento())
                        .idPessoaCancelamento(cancelamentoEntity.getIdPessoaCancelamento())
                        .dataHoraCancelamento(cancelamentoEntity.getDataHoraCancelamento())
                        .motivoCancelamento(cancelamentoEntity.getMotivoCancelamento())
                        .build())
                .metadados(entity.getMetadados())
                .build();
    }
}
