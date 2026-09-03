package br.com.srportto.contratoquery.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.srportto.contratoquery.domain.enums.CampoOrdenacao;
import br.com.srportto.contratoquery.domain.enums.DirecaoOrdenacao;
import br.com.srportto.contratoquery.domain.exception.BusinessException;

@DisplayName("Testes do Ordenacao")
class OrdenacaoTest {

    @Test
    @DisplayName("padrao() devolve DATA_CRIACAO + DESC")
    void padraoDevolveDataCriacaoDesc() {
        Ordenacao ordenacao = Ordenacao.padrao();

        assertEquals(CampoOrdenacao.DATA_CRIACAO, ordenacao.campo());
        assertEquals(DirecaoOrdenacao.DESC, ordenacao.direcao());
    }

    @Test
    @DisplayName("Campo isolado usa DESC como direção")
    void campoIsoladoUsaDesc() {
        Ordenacao ordenacao = Ordenacao.de("valor");

        assertEquals(CampoOrdenacao.VALOR, ordenacao.campo());
        assertEquals(DirecaoOrdenacao.DESC, ordenacao.direcao());
    }

    @ParameterizedTest
    @CsvSource({
            "dataCriacao,DATA_CRIACAO",
            "dataHoraInclusao,DATA_CRIACAO",
            "valor,VALOR",
            "valorAutorizacao,VALOR",
            "idAutorizacao,ID_AUTORIZACAO",
            "dataInicioVigencia,DATA_INICIO_VIGENCIA",
            "dataFimVigencia,DATA_FIM_VIGENCIA",
            "idPessoaRecebedora,ID_PESSOA_RECEBEDORA",
            "status,STATUS"
    })
    @DisplayName("Mapeia todos os aliases de campo aceitos")
    void mapeiaTodosOsAliases(String alias, CampoOrdenacao esperado) {
        Ordenacao ordenacao = Ordenacao.de(alias + ",asc");

        assertEquals(esperado, ordenacao.campo());
        assertEquals(DirecaoOrdenacao.ASC, ordenacao.direcao());
    }

    @ParameterizedTest
    @ValueSource(strings = {"valor,ASC", "valor,Desc", "valor, asc"})
    @DisplayName("Direção válida é aceita em qualquer caixa e com espaço após a vírgula")
    void direcaoValidaEmQualquerCaixaEComEspaco(String expressao) {
        Ordenacao ordenacao = Ordenacao.de(expressao);

        assertEquals(CampoOrdenacao.VALOR, ordenacao.campo());
    }

    @Test
    @DisplayName("Campo desconhecido é rejeitado")
    void campoDesconhecidoEhRejeitado() {
        assertThrows(BusinessException.class, () -> Ordenacao.de("campoInexistente,asc"));
    }

    @Test
    @DisplayName("Direção desconhecida é rejeitada")
    void direcaoDesconhecidaEhRejeitada() {
        assertThrows(BusinessException.class, () -> Ordenacao.de("valor,ascc"));
    }

    @Test
    @DisplayName("Direção vazia após a vírgula é rejeitada (D4: valor,)")
    void direcaoVaziaEhRejeitada() {
        assertThrows(BusinessException.class, () -> Ordenacao.de("valor,"));
    }

    @Test
    @DisplayName("Campo vazio antes da vírgula é rejeitado (D4: ,asc)")
    void campoVazioEhRejeitado() {
        assertThrows(BusinessException.class, () -> Ordenacao.de(",asc"));
    }

    @Test
    @DisplayName("Expressão com mais de duas partes é rejeitada (D4: valor,asc,extra)")
    void maisDeDuasPartesEhRejeitado() {
        assertThrows(BusinessException.class, () -> Ordenacao.de("valor,asc,extra"));
    }
}
