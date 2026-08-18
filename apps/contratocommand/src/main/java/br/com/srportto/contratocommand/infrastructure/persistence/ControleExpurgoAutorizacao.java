package br.com.srportto.contratocommand.infrastructure.persistence;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ControleExpurgoAutorizacao {

  public static int obterParticaoExpurgoWrite(LocalDate dataFinalizacao) {
        // Semanas desde o Epoch, na "gaveta" 0-99, deslocada para o range 900-999.
        long semanasTotais = ChronoUnit.WEEKS.between(LocalDate.ofEpochDay(0), dataFinalizacao);
        int gaveta = (int) (semanasTotais % 100);
        return 900 + gaveta;
    }

}
