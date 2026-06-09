package br.com.srportto.contratoquery.domain.utilities;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class AchaQtdeSemanas {

    public static int obterQtdeSemanasAte9999() {
        int semanasTotais = (int) ChronoUnit.WEEKS.between(LocalDate.ofEpochDay(0), LocalDate.of(9999, 12, 31));
        return semanasTotais;
    }

    public static int obterQtdeSemanasAteEntreDuasDatas(LocalDate data1, LocalDate data2) {
        int semanasTotais = (int) ChronoUnit.WEEKS.between(data1, data2);
        return semanasTotais;
    }
}
