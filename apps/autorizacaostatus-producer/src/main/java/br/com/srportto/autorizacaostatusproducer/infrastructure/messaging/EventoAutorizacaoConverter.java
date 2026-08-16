package br.com.srportto.autorizacaostatusproducer.infrastructure.messaging;

import br.com.srportto.autorizacaostatusproducer.domain.model.EventoAutorizacao;
import org.springframework.stereotype.Component;

/**
 * Converte o payload JSON consumido da fila para o modelo de domínio puro EventoAutorizacao
 * (D2-b de design.md) — primeiro dos dois mapeamentos do fluxo de tradução (payload -> domínio
 * -> Avro). Nenhum ajuste de formato de fio (ex.: setScale defensivo) acontece aqui: isso é
 * particularidade do Avro e fica em {@link EventoAutorizacaoAvroMapper}.
 */
@Component
public class EventoAutorizacaoConverter {

    public EventoAutorizacao converter(AutorizacaoEventoPayload payload) {
        return new EventoAutorizacao(
                payload.idAutorizacao(),
                payload.idParticaoConta(),
                payload.dataFimVigencia(),
                payload.tipoProduto(),
                payload.tipoJornada(),
                payload.status(),
                payload.motivoStatus(),
                payload.dataInicioVigencia(),
                payload.dataHoraInclusao(),
                payload.dataHoraUltimaAtualizacao(),
                payload.valorAutorizacao(),
                payload.idAutorizacaoEmpresa(),
                payload.valorLimite(),
                paraInt(payload.frequenciaPagamento()),
                paraInt(payload.quantidadeDividasCiclo()),
                paraInt(payload.indicadorUsoLimiteConta()),
                paraInt(payload.indicadorTipoMensageria()),
                payload.codigoCanalContratacao(),
                payload.descricao(),
                payload.idUnicoContaContratante(),
                payload.idPessoaPagadora(),
                payload.idPessoaDevedora(),
                payload.idPessoaRecebedora(),
                payload.codigoCanalCancelamento(),
                payload.idPessoaCancelamento(),
                payload.dataHoraCancelamento(),
                payload.motivoCancelamento(),
                payload.metadados() == null ? null : payload.metadados().toString());
    }

    private Integer paraInt(Short valor) {
        return valor == null ? null : valor.intValue();
    }

}
