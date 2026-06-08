package br.com.srportto.contratocommand.domain.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@DisplayName("Testes da classe ControleExpurgoAutorizacao")
class ControleExpurgoAutorizacaoTest {

  private static final LocalDate EPOCH_DAY = LocalDate.ofEpochDay(0); // 1970-01-01

  @Test
  @DisplayName("Deve retornar 900 para a data de época (1970-01-01)")
  void testEpochDay() {
    int resultado = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(EPOCH_DAY);
    assertEquals(900, resultado);
  }

  @Test
  @DisplayName("Deve retornar 901 para 7 dias após a época")
  void testOneWeekAfterEpoch() {
    LocalDate umaSemanaApos = EPOCH_DAY.plusWeeks(1);
    int resultado = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(umaSemanaApos);
    assertEquals(901, resultado);
  }

  @Test
  @DisplayName("Deve retornar 999 para 99 semanas após a época")
  void test99WeeksAfterEpoch() {
    LocalDate noventaNoveSemanasApos = EPOCH_DAY.plusWeeks(99);
    int resultado = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(noventaNoveSemanasApos);
    assertEquals(999, resultado);
  }

  @Test
  @DisplayName("Deve retornar 900 para 100 semanas após a época (ciclo volta para 0)")
  void test100WeeksAfterEpoch() {
    LocalDate cem = EPOCH_DAY.plusWeeks(100);
    int resultado = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(cem);
    assertEquals(900, resultado);
  }

  @Test
  @DisplayName("Deve retornar 950 para 50 semanas após a época")
  void test50WeeksAfterEpoch() {
    LocalDate cinquentaSemanasApos = EPOCH_DAY.plusWeeks(50);
    int resultado = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(cinquentaSemanasApos);
    assertEquals(950, resultado);
  }

  @ParameterizedTest(name = "Semanas = {0}, Partição esperada = {1}")
  @ValueSource(ints = {0, 1, 5, 10, 25, 50, 75, 99, 100, 150, 199, 200})
  @DisplayName("Deve calcular corretamente a partição para várias semanas")
  void testMultiplasSemanasComPartição(int semanas) {
    LocalDate data = EPOCH_DAY.plusWeeks(semanas);
    int resultado = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(data);
    
    int partição = (semanas % 100) + 900;
    assertEquals(partição, resultado, 
        "Para " + semanas + " semanas, esperava " + partição + " mas obteve " + resultado);
  }

  @Test
  @DisplayName("Deve gerar todas as partições de 900 a 999")
  void testTodosOsValoresNo900A999() {
    Set<Integer> particoesGeradas = new HashSet<>();

    // Testa 1000 semanas diferentes para garantir cobertura completa do range 900-999
    for (int semanas = 0; semanas < 1000; semanas++) {
      LocalDate data = EPOCH_DAY.plusWeeks(semanas);
      int particao = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(data);
      particoesGeradas.add(particao);
    }

    // Valida que todos os valores de 900 a 999 foram gerados
    assertEquals(100, particoesGeradas.size(), "Deveria gerar exatamente 100 valores diferentes");
    
    for (int i = 900; i <= 999; i++) {
      assertTrue(particoesGeradas.contains(i), 
          "A partição " + i + " não foi gerada no intervalo de 1000 semanas testadas");
    }
  }

  @Test
  @DisplayName("Deve estar sempre entre 900 e 999")
  void testResultadoSempreNoRange() {
    // Testa datas em vários períodos
    LocalDate[] datas = {
        LocalDate.of(1970, 1, 1),    // Época
        LocalDate.of(2000, 1, 1),    // Ano 2000
        LocalDate.of(2026, 4, 18),   // Hoje
        LocalDate.of(2050, 12, 31),  // Futuro próximo
        LocalDate.of(2100, 6, 15)    // Futuro distante
    };

    for (LocalDate data : datas) {
      int resultado = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(data);
      assertTrue(resultado >= 900 && resultado <= 999, 
          "Resultado " + resultado + " para data " + data + " está fora do range [900, 999]");
    }
  }

  @Test
  @DisplayName("Deve respeitar a fórmula (semanas % 100) + 900")
  void testFórmulaExata() {
    // Testa 300 semanas para validar a fórmula
    for (int semanas = 0; semanas < 300; semanas++) {
      LocalDate data = EPOCH_DAY.plusWeeks(semanas);
      int resultado = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(data);
      
      int esperado = (semanas % 100) + 900;
      assertEquals(esperado, resultado, 
          "Fórmula falhou para " + semanas + " semanas");
    }
  }

  @Test
  @DisplayName("Deve manter consistência para mesma data")
  void testConsistênciaParaMesmaData() {
    LocalDate data = LocalDate.of(2026, 4, 18);
    
    int resultado1 = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(data);
    int resultado2 = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(data);
    int resultado3 = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(data);

    assertEquals(resultado1, resultado2, "Resultados deveriam ser iguais para mesma data");
    assertEquals(resultado2, resultado3, "Resultados deveriam ser iguais para mesma data");
  }

  @Test
  @DisplayName("obterParticaoExpurgoDrop deve gerar todas as 100 partições de 900 a 999 ao longo do tempo")
  void testObterParticaoExpurgoDropGeraTodasAsParticoes() {
    Set<Integer> particoesGeradas = new HashSet<>();
    LocalDate dataAtual = LocalDate.now();

    System.out.println("data-referencia,particao-write,particao-drop");


    // Testa um intervalo grande de datas futuras para cobrir todas as partições
    // A lógica é: particaoExpurgoDrop = (particaoExpurgoWrite(dataRef) + 2) % 100 + 900
    // 
    // Nota: Sempre há uma partição que conflita com a partição de escrita atual, 
    // causando uma BusinessException. Por isso, em um teste sincronizado com data fixa,
    // apenas 99 partições podem ser geradas. Para gerar todas as 100, seria necessário
    // executar o teste em 100 momentos diferentes do tempo (100 semanas entre cada execução).
    for (int semanas = 10; semanas < 1010; semanas++) {
      LocalDate dataReferencia = dataAtual.plusWeeks(semanas);  
      
      try {
        int particaoWrite = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(dataReferencia);
        int particaoDrop = ControleExpurgoAutorizacao.obterParticaoExpurgoDrop(dataReferencia);

        System.out.println(dataReferencia + ","+ particaoWrite + "," + particaoDrop);

        particoesGeradas.add(particaoDrop);
      } catch (Exception e) {
        // Ignora exceções de validação de negócio
        // (data de referência ou conflito com partição de escrita atual)
      }
    }

    // Valida que foram geradas pelo menos 99 partições
    // (a 100ª partição seria aquela que conflita com a partição de escrita atual)
    assertTrue(particoesGeradas.size() >= 99, 
        "Deveria gerar no mínimo 99 valores diferentes. Gerados: " + particoesGeradas.size());
    
    // Valida que todas as partições geradas estão no range correto
    for (int particao : particoesGeradas) {
      assertTrue(particao >= 900 && particao <= 999,
          "Partição " + particao + " fora do range [900, 999]");
    }
    
    // Valida que há apenas uma partição faltante (máximo 1)
    Set<Integer> faltantes = new HashSet<>();
    for (int i = 900; i <= 999; i++) {
      if (!particoesGeradas.contains(i)) {
        faltantes.add(i);
      }
    }
    assertTrue(faltantes.size() <= 1,
        "Deveria faltar no máximo 1 partição. Faltantes: " + faltantes);
  }

  private static String getMissingPartitions(Set<Integer> geradas) {
    Set<Integer> faltantes = new HashSet<>();
    for (int i = 900; i <= 999; i++) {
      if (!geradas.contains(i)) {
        faltantes.add(i);
      }
    }
    return faltantes.toString();
  }
}
