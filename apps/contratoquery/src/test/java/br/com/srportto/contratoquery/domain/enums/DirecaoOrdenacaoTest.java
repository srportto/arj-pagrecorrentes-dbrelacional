package br.com.srportto.contratoquery.domain.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.srportto.contratoquery.domain.exception.BusinessException;

@DisplayName("Testes do DirecaoOrdenacao")
class DirecaoOrdenacaoTest {

    @ParameterizedTest
    @ValueSource(strings = {"asc", "ASC", "Asc", " asc "})
    @DisplayName("Aceita 'asc' em qualquer caixa, com trim")
    void aceitaAscEmQualquerCaixa(String valor) {
        assertEquals(DirecaoOrdenacao.ASC, DirecaoOrdenacao.porNome(valor));
    }

    @ParameterizedTest
    @ValueSource(strings = {"desc", "DESC", "Desc", " desc "})
    @DisplayName("Aceita 'desc' em qualquer caixa, com trim")
    void aceitaDescEmQualquerCaixa(String valor) {
        assertEquals(DirecaoOrdenacao.DESC, DirecaoOrdenacao.porNome(valor));
    }

    @Test
    @DisplayName("Rejeita valor desconhecido com BusinessException")
    void rejeitaValorDesconhecido() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> DirecaoOrdenacao.porNome("ascc"));

        assertEquals("Direção de ordenação inválida: ascc. Direções aceitas: asc, desc", ex.getMessage());
    }

    @Test
    @DisplayName("Rejeita direção vazia com BusinessException")
    void rejeitaValorVazio() {
        assertThrows(BusinessException.class, () -> DirecaoOrdenacao.porNome(""));
    }
}
