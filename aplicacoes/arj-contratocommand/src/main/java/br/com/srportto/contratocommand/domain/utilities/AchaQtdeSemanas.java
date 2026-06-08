package br.com.srportto.contratocommand.domain.utilities;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class AchaQtdeSemanas {

    public static int obterQtdeSemanasAte9999() {
        // O ChronoUnit.WEEKS calcula o número exato de semanas totais
        // entre o Epoch (01/01/1970) e 31/12/9999
        int semanasTotais = (int) ChronoUnit.WEEKS.between(LocalDate.ofEpochDay(0), LocalDate.of(9999, 12, 31));

        return semanasTotais;
    }

    public static int obterQtdeSemanasAteEntreDuasDatas(LocalDate data1, LocalDate data2) {
        // O ChronoUnit.WEEKS calcula o número exato de semanas totais
        // entre as duas datas
        int semanasTotais = (int) ChronoUnit.WEEKS.between(data1, data2);

        return semanasTotais;
    }
}
