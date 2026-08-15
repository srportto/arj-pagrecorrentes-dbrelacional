package br.com.srportto.contratocommand.domain.service;

public interface Rule<T> {
    String getLogCode();

    default boolean aceita(T objeto) {
        return true;
    }

    void validar(T objeto);

}
