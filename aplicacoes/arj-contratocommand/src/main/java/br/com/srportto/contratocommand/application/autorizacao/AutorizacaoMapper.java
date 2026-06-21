package br.com.srportto.contratocommand.application.autorizacao;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.enums.MotivoStatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Mapper único request→entidade compartilhado por todos os produtos. O mapeamento é idêntico
 * para PIX_AUTO e DDA_AUTO (mesma entidade {@link Autorizacao}); por isso não há mapper por produto.
 */
@Mapper(componentModel = "spring")
public interface AutorizacaoMapper {

    @Mapping(source = "valor", target = "valorAutorizacao")
    @Mapping(source = "frequencia", target = "frequenciaPagamento")
    @Mapping(source = "quantidadeDividasCiclo", target = "quantidadeDividasCiclo")
    @Mapping(source = "indicadorUsoLimiteConta", target = "indicadorUsoLimiteConta")
    @Mapping(target = "idAutorizacao", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "motivoStatus", ignore = true)
    @Mapping(target = "dataInicioVigencia", ignore = true)
    @Mapping(source = "dataFimVigencia", target = "dataFimVigencia")
    @Mapping(target = "dataHoraInclusao", ignore = true)
    @Mapping(target = "dataHoraUltimaAtualizacao", ignore = true)
    @Mapping(target = "indicadorTipoMensageria", ignore = true)
    @Mapping(target = "cancelamento", ignore = true)
    @Mapping(target = "metadados", ignore = true)
    Autorizacao toDomain(CriarAutorizacaoRequest request);

    @AfterMapping
    default void afterMapping(CriarAutorizacaoRequest request, @MappingTarget Autorizacao autorizacao) {

        autorizacao.setTipoProduto(TipoProduto.obterTipoProdutoEnumPorNome(request.tipoProduto()));

        if (request.metadados() != null) {
            autorizacao.setMetadados(request.metadados().toString());
        }

        autorizacao.inicializaCriacao(autorizacao);

        var motivo = MotivoStatusAutorizacao.obterMotivoStatusEnumPorIdMotivo(
                request.tipoJornada().getCodigoJornada());
        autorizacao.setMotivoStatus(motivo.name());
    }

}
