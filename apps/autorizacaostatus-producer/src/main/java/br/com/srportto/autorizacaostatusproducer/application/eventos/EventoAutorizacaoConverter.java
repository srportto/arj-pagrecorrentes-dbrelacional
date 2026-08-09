package br.com.srportto.autorizacaostatusproducer.application.eventos;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converte o payload JSON consumido da fila para o record Avro EventoAutorizacao, que
 * espelha a linha da tabela autorizacoes. setScale defensivo nos decimais: o serializer
 * Avro exige scale exata (2) e o JSON pode entregar um scale diferente.
 */
@Component
public class EventoAutorizacaoConverter {

    public EventoAutorizacao converter(AutorizacaoEventoPayload payload) {
        return EventoAutorizacao.newBuilder()
                .setIdAutorizacao(payload.idAutorizacao())
                .setIdParticaoConta(payload.idParticaoConta())
                .setDataFimVigencia(payload.dataFimVigencia())
                .setTipoProduto(payload.tipoProduto())
                .setTipoJornada(payload.tipoJornada())
                .setStatus(payload.status())
                .setMotivoStatus(payload.motivoStatus())
                .setDataInicioVigencia(payload.dataInicioVigencia())
                .setDataHoraInclusao(payload.dataHoraInclusao())
                .setDataHoraUltimaAtlz(payload.dataHoraUltimaAtualizacao())
                .setValor(escalar(payload.valorAutorizacao()))
                .setIdAutorizacaoEmpresa(payload.idAutorizacaoEmpresa())
                .setValorLimite(escalar(payload.valorLimite()))
                .setFrequencia(paraInt(payload.frequenciaPagamento()))
                .setQuantidadeDividasCiclo(paraInt(payload.quantidadeDividasCiclo()))
                .setIndicadorUsoLimiteConta(paraInt(payload.indicadorUsoLimiteConta()))
                .setIndicadorTipoMensageria(paraInt(payload.indicadorTipoMensageria()))
                .setCodigoCanalContratacao(payload.codigoCanalContratacao())
                .setDescricao(payload.descricao())
                .setIdUnicoContaContratante(payload.idUnicoContaContratante())
                .setIdPessoaPagadora(payload.idPessoaPagadora())
                .setIdPessoaDevedora(payload.idPessoaDevedora())
                .setIdPessoaRecebedora(payload.idPessoaRecebedora())
                .setCodigoCanalCancelamento(payload.codigoCanalCancelamento())
                .setIdPessoaCancelamento(payload.idPessoaCancelamento())
                .setDataHoraCancelamento(payload.dataHoraCancelamento())
                .setMotivoCancelamento(payload.motivoCancelamento())
                .setMetadados(payload.metadados() == null ? null : payload.metadados().toString())
                .build();
    }

    private BigDecimal escalar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private Integer paraInt(Short valor) {
        return valor == null ? null : valor.intValue();
    }

}
