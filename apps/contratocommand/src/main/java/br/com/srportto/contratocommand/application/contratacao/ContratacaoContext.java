package br.com.srportto.contratocommand.application.contratacao;

import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;

/**
 * Contexto imutável da contratação (header + corpo). Sem enriquecimento pré-validação (ao
 * contrário do cancelamento), por isso não há wither.
 */
public record ContratacaoContext(
        TipoJornadaAutorizacao tipoJornada,
        CriarAutorizacaoRequest dados) {

    public static ContratacaoContext doRequest(TipoJornadaAutorizacao tipoJornada, CriarAutorizacaoRequest dados) {
        return new ContratacaoContext(tipoJornada, dados);
    }
}
