package br.com.srportto.contratocommand.application.usecase;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.enums.MotivoStatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoCommand;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Mapper único comando→entidade compartilhado por todos os produtos. O mapeamento é idêntico
 * para PIX_AUTO e DDA_AUTO (mesma entidade {@link Autorizacao}); por isso não há mapper por produto.
 */
@Mapper(componentModel = "spring")
public interface AutorizacaoMapper {

    @Mapping(source = "valor", target = "valorAutorizacao")
    @Mapping(source = "frequencia", target = "frequenciaPagamento")
    @Mapping(source = "quantidadeDividasCiclo", target = "quantidadeDividasCiclo")
    @Mapping(source = "indicadorUsoLimiteConta", target = "indicadorUsoLimiteConta")
    @Mapping(source = "descricao", target = "descricao")
    @Mapping(target = "idAutorizacao", ignore = true)
    // Evita Enum.valueOf implícito do MapStruct (case-sensitive); resolvido no afterMapping.
    @Mapping(target = "tipoProduto", ignore = true)
    // Setado explicitamente no afterMapping, mesmo motivo de tipoProduto.
    @Mapping(target = "tipoJornada", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "motivoStatus", ignore = true)
    @Mapping(target = "dataInicioVigencia", ignore = true)
    @Mapping(source = "dataFimVigencia", target = "dataFimVigencia")
    @Mapping(target = "dataHoraInclusao", ignore = true)
    @Mapping(target = "dataHoraUltimaAtualizacao", ignore = true)
    @Mapping(target = "indicadorTipoMensageria", ignore = true)
    @Mapping(target = "cancelamento", ignore = true)
    @Mapping(target = "metadados", ignore = true)
    Autorizacao toDomain(CriarAutorizacaoCommand command);

    @AfterMapping
    default void afterMapping(CriarAutorizacaoCommand command, @MappingTarget Autorizacao autorizacao) {

        autorizacao.setTipoProduto(TipoProduto.obterTipoProdutoEnumPorNome(command.tipoProduto()));
        autorizacao.setTipoJornada(command.tipoJornada());

        if (command.metadados() != null) {
            autorizacao.setMetadados(command.metadados());
        }

        autorizacao.inicializaCriacao();

        var motivo = MotivoStatusAutorizacao.obterMotivoStatusEnumPorIdMotivo(
                command.tipoJornada().getCodigoJornada());
        autorizacao.setMotivoStatus(motivo.name());
    }

}
