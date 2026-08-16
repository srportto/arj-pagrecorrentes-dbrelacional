package br.com.srportto.autorizacaostatusproducer.infrastructure.messaging;

import br.com.srportto.autorizacaostatusproducer.domain.exception.EventoAutorizacaoInvalidoException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida campos obrigatórios do schema Avro antes de converter/gerar key/produzir.
 *
 * <p>Necessário porque o builder do {@code avro-maven-plugin} só valida <em>ausência</em>
 * de {@code set}, não {@code null} explícito — um campo nulo passaria em silêncio e só
 * falharia adiante (fora da classificação de erro), causando reentrega infinita.
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
        // campos sem union ["null", X] no schema — obrigatórios
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
     * Limite de {@value #MAX_DIGITOS_INTEIROS} dígitos inteiros (decimal 17,2 do .avsc).
     * O converter normaliza escala, não precisão — estourar isso quebra dentro de
     * {@code Producer.send()}, fora da classificação de erro, travando a mensagem.
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
