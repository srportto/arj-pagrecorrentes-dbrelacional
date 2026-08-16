package br.com.srportto.autorizacaostatusproducer.infrastructure.messaging;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converte o modelo de domínio puro EventoAutorizacao para o record Avro publicado no Kafka —
 * segundo dos dois mapeamentos do fluxo de tradução (D2-b de design.md). setScale defensivo
 * nos decimais: o serializer Avro exige scale exata (2), particularidade do formato de fio que
 * não pertence ao modelo de domínio.
 */
@Component
public class EventoAutorizacaoAvroMapper {

    public br.com.srportto.eventos.autorizacao.EventoAutorizacao paraAvro(
            br.com.srportto.autorizacaostatusproducer.domain.model.EventoAutorizacao evento) {
        return br.com.srportto.eventos.autorizacao.EventoAutorizacao.newBuilder()
                .setIdAutorizacao(evento.idAutorizacao())
                .setIdParticaoConta(evento.idParticaoConta())
                .setDataFimVigencia(evento.dataFimVigencia())
                .setTipoProduto(evento.tipoProduto())
                .setTipoJornada(evento.tipoJornada())
                .setStatus(evento.status())
                .setMotivoStatus(evento.motivoStatus())
                .setDataInicioVigencia(evento.dataInicioVigencia())
                .setDataHoraInclusao(evento.dataHoraInclusao())
                .setDataHoraUltimaAtlz(evento.dataHoraUltimaAtlz())
                .setValor(escalar(evento.valor()))
                .setIdAutorizacaoEmpresa(evento.idAutorizacaoEmpresa())
                .setValorLimite(escalar(evento.valorLimite()))
                .setFrequencia(evento.frequencia())
                .setQuantidadeDividasCiclo(evento.quantidadeDividasCiclo())
                .setIndicadorUsoLimiteConta(evento.indicadorUsoLimiteConta())
                .setIndicadorTipoMensageria(evento.indicadorTipoMensageria())
                .setCodigoCanalContratacao(evento.codigoCanalContratacao())
                .setDescricao(evento.descricao())
                .setIdUnicoContaContratante(evento.idUnicoContaContratante())
                .setIdPessoaPagadora(evento.idPessoaPagadora())
                .setIdPessoaDevedora(evento.idPessoaDevedora())
                .setIdPessoaRecebedora(evento.idPessoaRecebedora())
                .setCodigoCanalCancelamento(evento.codigoCanalCancelamento())
                .setIdPessoaCancelamento(evento.idPessoaCancelamento())
                .setDataHoraCancelamento(evento.dataHoraCancelamento())
                .setMotivoCancelamento(evento.motivoCancelamento())
                .setMetadados(evento.metadados())
                .build();
    }

    private BigDecimal escalar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }

}
