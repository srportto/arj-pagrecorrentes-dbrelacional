package br.com.srportto.contratocommand.application;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.enums.MotivoStatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
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

    @Mapping(source = "dados.valor", target = "valorAutorizacao")
    @Mapping(source = "dados.frequencia", target = "frequenciaPagamento")
    @Mapping(source = "dados.quantidadeDividasCiclo", target = "quantidadeDividasCiclo")
    @Mapping(source = "dados.indicadorUsoLimiteConta", target = "indicadorUsoLimiteConta")
    @Mapping(source = "dados.descricao", target = "descricao")
    @Mapping(target = "idAutorizacao", ignore = true)
    // Evita Enum.valueOf implícito do MapStruct (case-sensitive); resolvido no afterMapping.
    @Mapping(target = "tipoProduto", ignore = true)
    // Setado explicitamente no afterMapping, mesmo motivo de tipoProduto.
    @Mapping(target = "tipoJornada", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "motivoStatus", ignore = true)
    @Mapping(target = "dataInicioVigencia", ignore = true)
    @Mapping(source = "dados.dataFimVigencia", target = "dataFimVigencia")
    @Mapping(target = "dataHoraInclusao", ignore = true)
    @Mapping(target = "dataHoraUltimaAtualizacao", ignore = true)
    @Mapping(target = "indicadorTipoMensageria", ignore = true)
    @Mapping(target = "cancelamento", ignore = true)
    @Mapping(target = "metadados", ignore = true)
    Autorizacao toDomain(CriarAutorizacaoRequest dados, TipoJornadaAutorizacao tipoJornada);

    @AfterMapping
    default void afterMapping(CriarAutorizacaoRequest dados, TipoJornadaAutorizacao tipoJornada,
            @MappingTarget Autorizacao autorizacao) {

        autorizacao.setTipoProduto(TipoProduto.obterTipoProdutoEnumPorNome(dados.tipoProduto()));
        autorizacao.setTipoJornada(tipoJornada);

        if (dados.metadados() != null) {
            autorizacao.setMetadados(dados.metadados().toString());
        }

        autorizacao.inicializaCriacao();

        var motivo = MotivoStatusAutorizacao.obterMotivoStatusEnumPorIdMotivo(
                tipoJornada.getCodigoJornada());
        autorizacao.setMotivoStatus(motivo.name());
    }

}
