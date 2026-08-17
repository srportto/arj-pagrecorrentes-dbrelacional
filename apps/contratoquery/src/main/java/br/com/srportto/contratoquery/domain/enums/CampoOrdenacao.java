package br.com.srportto.contratoquery.domain.enums;

/**
 * Campos pelos quais a listagem de autorizações pode ser ordenada — vocabulário de domínio, sem
 * conhecimento do caminho JPA/coluna física que cada um resolve na infraestrutura (D3/D4 da porta
 * {@code AutorizacaoRepository}: a tradução para caminho de propriedade fica no adaptador).
 */
public enum CampoOrdenacao {
    DATA_CRIACAO,
    VALOR,
    ID_AUTORIZACAO,
    DATA_INICIO_VIGENCIA,
    DATA_FIM_VIGENCIA,
    ID_PESSOA_RECEBEDORA,
    STATUS
}
