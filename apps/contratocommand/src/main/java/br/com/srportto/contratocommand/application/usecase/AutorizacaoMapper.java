package br.com.srportto.contratocommand.application.usecase;

import br.com.srportto.contratocommand.domain.enums.MotivoStatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.model.Autorizacao;
import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoCommand;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.UUID;

/**
 * Mapper único comando→modelo de domínio compartilhado por todos os produtos. O mapeamento é
 * idêntico para PIX_AUTO e DDA_AUTO (mesmo modelo {@link Autorizacao}); por isso não há mapper por
 * produto.
 */
@Mapper(componentModel = "spring")
public interface AutorizacaoMapper {

    @Mapping(source = "command.valor", target = "valorAutorizacao")
    @Mapping(source = "command.frequencia", target = "frequenciaPagamento")
    @Mapping(source = "command.quantidadeDividasCiclo", target = "quantidadeDividasCiclo")
    @Mapping(source = "command.indicadorUsoLimiteConta", target = "indicadorUsoLimiteConta")
    @Mapping(source = "command.descricao", target = "descricao")
    @Mapping(source = "command.metadados", target = "metadados")
    @Mapping(target = "idAutorizacao", ignore = true)
    @Mapping(target = "idParticaoConta", ignore = true)
    // Evita Enum.valueOf implícito do MapStruct (case-sensitive); resolvido no afterMapping.
    @Mapping(target = "tipoProduto", ignore = true)
    // Setado explicitamente no afterMapping, mesmo motivo de tipoProduto.
    @Mapping(target = "tipoJornada", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "motivoStatus", ignore = true)
    @Mapping(target = "dataInicioVigencia", ignore = true)
    @Mapping(source = "command.dataFimVigencia", target = "dataFimVigencia")
    @Mapping(target = "dataHoraInclusao", ignore = true)
    @Mapping(target = "dataHoraUltimaAtualizacao", ignore = true)
    @Mapping(target = "indicadorTipoMensageria", ignore = true)
    @Mapping(target = "cancelamento", ignore = true)
    @Mapping(target = "version", ignore = true)
    Autorizacao toDomain(CriarAutorizacaoCommand command, UUID idGerado);

    @AfterMapping
    default void afterMapping(CriarAutorizacaoCommand command, UUID idGerado, @MappingTarget Autorizacao autorizacao) {

        autorizacao.setTipoProduto(TipoProduto.obterTipoProdutoEnumPorNome(command.tipoProduto()));
        autorizacao.setTipoJornada(command.tipoJornada());

        autorizacao.inicializaCriacao(idGerado);

        var motivo = MotivoStatusAutorizacao.obterMotivoStatusEnumPorIdMotivo(
                command.tipoJornada().getCodigoJornada());
        autorizacao.setMotivoStatus(motivo.name());
    }

}
