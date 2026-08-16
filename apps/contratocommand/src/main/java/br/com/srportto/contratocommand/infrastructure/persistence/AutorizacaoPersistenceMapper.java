package br.com.srportto.contratocommand.infrastructure.persistence;

import br.com.srportto.contratocommand.domain.model.Autorizacao;
import br.com.srportto.contratocommand.domain.model.Cancelamento;
import org.springframework.stereotype.Component;

/**
 * Conversão bidirecional entre {@link Autorizacao} (domínio puro) e {@link AutorizacaoJpaEntity}.
 * {@code version} trafega nos dois sentidos — sem ela, o {@code UPDATE} sai sem cláusula de
 * versão e o lock otimista para de funcionar silenciosamente (ver design.md, D1).
 *
 * {@code aplicarEm} é o único caminho para escrever cancelamento/decisão/expurgo: aplica os campos
 * do modelo sobre uma entidade JÁ GERENCIADA pelo Hibernate (dirty-checking produz o UPDATE
 * versionado no commit). É proibido montar uma entidade nova a partir do modelo com
 * {@code version} não nulo e salvá-la — isso reproduz a armadilha nº 11 (StaleObjectStateException
 * determinístico em instância detached).
 */
@Component
public class AutorizacaoPersistenceMapper {

    public Autorizacao paraDominio(AutorizacaoJpaEntity entity) {
        var idEntity = entity.getIdAutorizacao();
        var cancelamentoEntity = entity.getCancelamento();

        var modelo = new Autorizacao();
        modelo.setIdAutorizacao(idEntity.getIdAutorizacao());
        modelo.setIdParticaoConta(idEntity.getIdParticaoConta());
        modelo.setDataFimVigencia(entity.getDataFimVigencia());
        modelo.setTipoProduto(entity.getTipoProduto());
        modelo.setTipoJornada(entity.getTipoJornada());
        modelo.setStatus(entity.getStatus());
        modelo.setMotivoStatus(entity.getMotivoStatus());
        modelo.setDataInicioVigencia(entity.getDataInicioVigencia());
        modelo.setDataHoraInclusao(entity.getDataHoraInclusao());
        modelo.setDataHoraUltimaAtualizacao(entity.getDataHoraUltimaAtualizacao());
        modelo.setValorAutorizacao(entity.getValorAutorizacao());
        modelo.setIdAutorizacaoEmpresa(entity.getIdAutorizacaoEmpresa());
        modelo.setVersion(entity.getVersion());
        modelo.setValorLimite(entity.getValorLimite());
        modelo.setFrequenciaPagamento(entity.getFrequenciaPagamento());
        modelo.setQuantidadeDividasCiclo(entity.getQuantidadeDividasCiclo());
        modelo.setIndicadorUsoLimiteConta(entity.getIndicadorUsoLimiteConta());
        modelo.setIndicadorTipoMensageria(entity.getIndicadorTipoMensageria());
        modelo.setCodigoCanalContratacao(entity.getCodigoCanalContratacao());
        modelo.setDescricao(entity.getDescricao());
        modelo.setIdUnicoContaContratante(entity.getIdUnicoContaContratante());
        modelo.setIdPessoaPagadora(entity.getIdPessoaPagadora());
        modelo.setIdPessoaDevedora(entity.getIdPessoaDevedora());
        modelo.setIdPessoaRecebedora(entity.getIdPessoaRecebedora());
        if (cancelamentoEntity != null) {
            modelo.setCancelamento(new Cancelamento(
                    cancelamentoEntity.getCodigoCanalCancelamento(),
                    cancelamentoEntity.getIdPessoaCancelamento(),
                    cancelamentoEntity.getDataHoraCancelamento(),
                    cancelamentoEntity.getMotivoCancelamento()));
        }
        modelo.setMetadados(entity.getMetadados());

        return modelo;
    }

    /** Só para criação: {@code version} SHALL ser nula no modelo, para o Hibernate escolher persist (insert). */
    public AutorizacaoJpaEntity paraEntidade(Autorizacao modelo) {
        var particao = ReversibleUUIDv7.extract(modelo.getIdAutorizacao());
        var idEntity = new IdAutorizacaoJpaEmbeddable(modelo.getIdAutorizacao(), particao);

        var entity = new AutorizacaoJpaEntity();
        entity.setIdAutorizacao(idEntity);
        entity.setDataFimVigencia(modelo.getDataFimVigencia());
        entity.setTipoProduto(modelo.getTipoProduto());
        entity.setTipoJornada(modelo.getTipoJornada());
        entity.setStatus(modelo.getStatus());
        entity.setMotivoStatus(modelo.getMotivoStatus());
        entity.setDataInicioVigencia(modelo.getDataInicioVigencia());
        entity.setDataHoraInclusao(modelo.getDataHoraInclusao());
        entity.setDataHoraUltimaAtualizacao(modelo.getDataHoraUltimaAtualizacao());
        entity.setValorAutorizacao(modelo.getValorAutorizacao());
        entity.setIdAutorizacaoEmpresa(modelo.getIdAutorizacaoEmpresa());
        entity.setValorLimite(modelo.getValorLimite());
        entity.setFrequenciaPagamento(modelo.getFrequenciaPagamento());
        entity.setQuantidadeDividasCiclo(modelo.getQuantidadeDividasCiclo());
        entity.setIndicadorUsoLimiteConta(modelo.getIndicadorUsoLimiteConta());
        entity.setIndicadorTipoMensageria(modelo.getIndicadorTipoMensageria());
        entity.setCodigoCanalContratacao(modelo.getCodigoCanalContratacao());
        entity.setDescricao(modelo.getDescricao());
        entity.setIdUnicoContaContratante(modelo.getIdUnicoContaContratante());
        entity.setIdPessoaPagadora(modelo.getIdPessoaPagadora());
        entity.setIdPessoaDevedora(modelo.getIdPessoaDevedora());
        entity.setIdPessoaRecebedora(modelo.getIdPessoaRecebedora());
        if (modelo.getCancelamento() != null) {
            var c = modelo.getCancelamento();
            entity.setCancelamento(new CancelamentoJpaEmbeddable(
                    c.getCodigoCanalCancelamento(), c.getIdPessoaCancelamento(),
                    c.getDataHoraCancelamento(), c.getMotivoCancelamento()));
        }
        entity.setMetadados(modelo.getMetadados());

        return entity;
    }

    /** Aplica os campos mutáveis do modelo sobre a entidade gerenciada — nunca mexe em id nem version. */
    public void aplicarEm(Autorizacao modelo, AutorizacaoJpaEntity entidadeGerenciada) {
        entidadeGerenciada.setDataFimVigencia(modelo.getDataFimVigencia());
        entidadeGerenciada.setTipoProduto(modelo.getTipoProduto());
        entidadeGerenciada.setTipoJornada(modelo.getTipoJornada());
        entidadeGerenciada.setStatus(modelo.getStatus());
        entidadeGerenciada.setMotivoStatus(modelo.getMotivoStatus());
        entidadeGerenciada.setDataInicioVigencia(modelo.getDataInicioVigencia());
        entidadeGerenciada.setDataHoraInclusao(modelo.getDataHoraInclusao());
        entidadeGerenciada.setDataHoraUltimaAtualizacao(modelo.getDataHoraUltimaAtualizacao());
        entidadeGerenciada.setValorAutorizacao(modelo.getValorAutorizacao());
        entidadeGerenciada.setIdAutorizacaoEmpresa(modelo.getIdAutorizacaoEmpresa());
        entidadeGerenciada.setValorLimite(modelo.getValorLimite());
        entidadeGerenciada.setFrequenciaPagamento(modelo.getFrequenciaPagamento());
        entidadeGerenciada.setQuantidadeDividasCiclo(modelo.getQuantidadeDividasCiclo());
        entidadeGerenciada.setIndicadorUsoLimiteConta(modelo.getIndicadorUsoLimiteConta());
        entidadeGerenciada.setIndicadorTipoMensageria(modelo.getIndicadorTipoMensageria());
        entidadeGerenciada.setCodigoCanalContratacao(modelo.getCodigoCanalContratacao());
        entidadeGerenciada.setDescricao(modelo.getDescricao());
        entidadeGerenciada.setIdUnicoContaContratante(modelo.getIdUnicoContaContratante());
        entidadeGerenciada.setIdPessoaPagadora(modelo.getIdPessoaPagadora());
        entidadeGerenciada.setIdPessoaDevedora(modelo.getIdPessoaDevedora());
        entidadeGerenciada.setIdPessoaRecebedora(modelo.getIdPessoaRecebedora());
        if (modelo.getCancelamento() != null) {
            var c = modelo.getCancelamento();
            entidadeGerenciada.setCancelamento(new CancelamentoJpaEmbeddable(
                    c.getCodigoCanalCancelamento(), c.getIdPessoaCancelamento(),
                    c.getDataHoraCancelamento(), c.getMotivoCancelamento()));
        }
        entidadeGerenciada.setMetadados(modelo.getMetadados());
    }

}
