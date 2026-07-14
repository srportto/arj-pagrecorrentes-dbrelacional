package br.com.srportto.contratocommand.shared.validationsetup;

import java.util.List;

public interface Validator<R extends Rule<T>, T> {

    String getLogCode();

    List<R> getRules();

    default void validar(T payloadValidacao) {
        for (R regra : getRules()) {
            if (regra.aceita(payloadValidacao)) {
                regra.validar(payloadValidacao);
            }
        }
    }

}
