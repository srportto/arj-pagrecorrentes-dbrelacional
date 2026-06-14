package br.com.srportto.contratoquery.domain.utilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import br.com.srportto.contratoquery.shared.exceptions.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do ControleExpurgoAutorizacao")
class ControleExpurgoAutorizacaoTest {

    @Test
    @DisplayName("obterParticaoExpurgoWrite produz partição na faixa 900..999")
    void writeNaFaixa() {
        LocalDate base = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 200; i++) {
            int particao = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(base.plusWeeks(i));
            assertTrue(particao >= 900 && particao <= 999, "fora da faixa: " + particao);
        }
    }

    @Test
    @DisplayName("obterParticaoExpurgoDrop lança BusinessException para data no passado")
    void dropPassadoLanca() {
        LocalDate ontem = LocalDate.now().minusDays(1);
        assertThrows(BusinessException.class,
                () -> ControleExpurgoAutorizacao.obterParticaoExpurgoDrop(ontem));
    }

    @Test
    @DisplayName("obterParticaoExpurgoDrop retorna partição válida (900..999) para data futura")
    void dropFuturoValido() {
        // 60 semanas à frente: longe da partição de escrita atual, evita colisão
        LocalDate futuro = LocalDate.now().plusWeeks(60);
        int drop = ControleExpurgoAutorizacao.obterParticaoExpurgoDrop(futuro);
        assertTrue(drop >= 900 && drop <= 999, "fora da faixa: " + drop);
    }

    @Test
    @DisplayName("obterParticaoExpurgoDrop aplica o wrap quando write+2 ultrapassa 999")
    void dropAplicaWrap() {
        // procura uma data futura cuja partição de escrita seja 998 ou 999 (força o ramo de wrap)
        LocalDate cursor = LocalDate.now().plusWeeks(1);
        boolean wrapCoberto = false;
        for (int i = 0; i < 210 && !wrapCoberto; i++) {
            LocalDate candidata = cursor.plusWeeks(i);
            int write = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(candidata);
            if (write >= 998) {
                try {
                    int drop = ControleExpurgoAutorizacao.obterParticaoExpurgoDrop(candidata);
                    assertTrue(drop >= 900 && drop <= 999, "wrap fora da faixa: " + drop);
                    wrapCoberto = true;
                } catch (BusinessException colisaoOuPassado) {
                    // colisão com a partição de escrita atual: segue procurando
                }
            }
        }
        assertTrue(wrapCoberto, "não foi possível cobrir o ramo de wrap no intervalo pesquisado");
    }
}
