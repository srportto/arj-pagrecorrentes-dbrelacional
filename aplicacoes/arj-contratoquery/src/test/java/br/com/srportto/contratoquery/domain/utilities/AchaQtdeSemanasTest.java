package br.com.srportto.contratoquery.domain.utilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do AchaQtdeSemanas")
class AchaQtdeSemanasTest {

    @Test
    @DisplayName("obterQtdeSemanasAte9999 retorna um total positivo e grande")
    void semanasAte9999() {
        int semanas = AchaQtdeSemanas.obterQtdeSemanasAte9999();
        assertTrue(semanas > 400_000, "esperado total grande de semanas até 9999, foi " + semanas);
    }

    @Test
    @DisplayName("obterQtdeSemanasAteEntreDuasDatas conta semanas completas entre datas")
    void semanasEntreDatas() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        assertEquals(10, AchaQtdeSemanas.obterQtdeSemanasAteEntreDuasDatas(inicio, inicio.plusWeeks(10)));
        assertEquals(0, AchaQtdeSemanas.obterQtdeSemanasAteEntreDuasDatas(inicio, inicio.plusDays(6)));
    }
}
