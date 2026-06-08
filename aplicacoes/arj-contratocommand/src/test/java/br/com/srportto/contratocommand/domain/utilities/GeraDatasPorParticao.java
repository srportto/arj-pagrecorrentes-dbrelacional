package br.com.srportto.contratocommand.domain.utilities;

import java.time.LocalDate;

/**
 * Utilitário para gerar uma data para cada partição de expurgo (900-999)
 */
public class GeraDatasPorParticao {

  public static void main(String[] args) {
    LocalDate epochDay = LocalDate.ofEpochDay(0); // 1970-01-01
    
    System.out.println("Mapeamento de Datas para Partições de Expurgo (900-999):");
    System.out.println("============================================================\n");
    System.out.println(String.format("%-12s,%s", "Data", "Partição"));
    System.out.println("-".repeat(30));

    //var numeroSemanasLimite = AchaQtdeSemanas.obterQtdeSemanasAte9999();
    var numeroSemanasLimite = AchaQtdeSemanas.obterQtdeSemanasAteEntreDuasDatas(epochDay, LocalDate.of(2100, 12, 31));

    for (int semana = 0; semana < numeroSemanasLimite; semana++) {
      LocalDate data = epochDay.plusWeeks(semana);
      int particao = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(data);
      
      System.out.println(String.format("%s,%d", data, particao));
    }



  }
}
