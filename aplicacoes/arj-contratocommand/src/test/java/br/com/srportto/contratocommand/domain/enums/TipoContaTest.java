package br.com.srportto.contratocommand.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do enum TipoConta")
class TipoContaTest {

    @Test
    @DisplayName("valueOf resolve cada constante e values lista todas")
    void valoresEValueOf() {
        assertEquals(3, TipoConta.values().length);
        assertEquals(TipoConta.CORRENTE, TipoConta.valueOf("CORRENTE"));
        assertEquals(TipoConta.POUPANCA, TipoConta.valueOf("POUPANCA"));
        assertEquals(TipoConta.SALARIO, TipoConta.valueOf("SALARIO"));
    }
}
