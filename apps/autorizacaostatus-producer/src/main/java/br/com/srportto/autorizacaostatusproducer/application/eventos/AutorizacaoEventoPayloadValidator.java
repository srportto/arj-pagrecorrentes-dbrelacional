package br.com.srportto.autorizacaostatusproducer.application.eventos;

import br.com.srportto.autorizacaostatusproducer.shared.exceptions.EventoAutorizacaoInvalidoException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida os campos obrigatórios do schema Avro logo após a desserialização, antes de
 * qualquer conversão, geração de key ou produce.
 *
 * <p>Necessário porque o builder gerado pelo {@code avro-maven-plugin} valida apenas a
 * <em>ausência</em> de {@code set} ({@code fieldSetFlags()}), não o valor {@code null}
 * explícito. Como {@link EventoAutorizacaoConverter} sempre chama os setters, um campo
 * obrigatório nulo produz um SpecificRecord inválido em silêncio, cuja falha só aparece
 * adiante e fora da classificação: NullPointerException na geração da key, ou
 * SerializationException <em>síncrona</em> dentro de {@code Producer.send()} (que o
 * tratamento de ExecutionException/TimeoutException não alcança). Ambas cairiam no catch
 * genérico do listener, impedindo o ack e causando reentrega infinita — a fila não tem
 * redrive policy.
 */
@Component
public class AutorizacaoEventoPayloadValidator {

    /** Espelha {@code decimal(precision, scale)} de valor/valor_limite no EventoAutorizacao.avsc. */
    private static final int PRECISION = 17;
    private static final int SCALE = 2;
    private static final int MAX_DIGITOS_INTEIROS = PRECISION - SCALE;

    public void validar(AutorizacaoEventoPayload payload) {
        if (payload == null) {
            throw new EventoAutorizacaoInvalidoException("Payload desserializado é nulo");
        }

        List<String> ausentes = new ArrayList<>();
        // campos sem union ["null", X] no EventoAutorizacao.avsc — obrigatorios no schema
        exigir(ausentes, "id_autorizacao", payload.idAutorizacao());
        exigir(ausentes, "id_particao_conta", payload.idParticaoConta());
        exigir(ausentes, "data_fim_vigencia", payload.dataFimVigencia());
        exigir(ausentes, "tipo_produto", payload.tipoProduto());
        exigir(ausentes, "status", payload.status());
        exigir(ausentes, "data_hora_inclusao", payload.dataHoraInclusao());
        exigir(ausentes, "data_hora_ultima_atlz", payload.dataHoraUltimaAtualizacao());
        exigirTexto(ausentes, "codigo_canal_contratacao", payload.codigoCanalContratacao());

        if (!ausentes.isEmpty()) {
            throw new EventoAutorizacaoInvalidoException(
                    "Campos obrigatórios do schema Avro ausentes ou nulos no payload: "
                            + String.join(", ", ausentes));
        }

        exigirDecimalNaFaixa("valor", payload.valorAutorizacao());
        exigirDecimalNaFaixa("valor_limite", payload.valorLimite());
    }

    /**
     * O .avsc declara {@code decimal(precision=17, scale=2)} — no máximo
     * {@value #MAX_DIGITOS_INTEIROS} dígitos inteiros. {@code EventoAutorizacaoConverter}
     * normaliza a escala, mas não a precisão: um valor maior estoura na conversão decimal
     * do Avro, que acontece <em>dentro</em> de {@code Producer.send()} e escaparia da
     * classificação, travando a mensagem em reentrega infinita.
     */
    private void exigirDecimalNaFaixa(String campo, BigDecimal valor) {
        if (valor == null) {
            return;
        }

        int digitosInteiros = valor.precision() - valor.scale();
        if (digitosInteiros > MAX_DIGITOS_INTEIROS) {
            throw new EventoAutorizacaoInvalidoException(
                    "Campo " + campo + " excede a precisão do schema Avro decimal(" + PRECISION + ","
                            + SCALE + "): " + digitosInteiros + " dígitos inteiros, máximo "
                            + MAX_DIGITOS_INTEIROS);
        }
    }

    private void exigir(List<String> ausentes, String campo, Object valor) {
        if (valor == null) {
            ausentes.add(campo);
        }
    }

    private void exigirTexto(List<String> ausentes, String campo, String valor) {
        if (!StringUtils.hasText(valor)) {
            ausentes.add(campo);
        }
    }

}
