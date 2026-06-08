package br.com.srportto.contratocommand.application.enabledproduct.pixauto;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;


import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;

@Mapper(componentModel = "spring")
public interface PixAutoMapper {

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

        autorizacao.setTipoProduto(TipoProduto.valueOf(request.tipoProduto()));

        if (request.metadados() != null) {
            autorizacao.setMetadados(request.metadados().toString());
        }

        autorizacao.inicializaCriacao(autorizacao);
    }

}
