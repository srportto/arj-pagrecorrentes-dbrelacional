package br.com.srportto.contratoquery.domain.model;

import br.com.srportto.contratoquery.domain.enums.CampoOrdenacao;
import br.com.srportto.contratoquery.domain.enums.DirecaoOrdenacao;
import br.com.srportto.contratoquery.domain.exception.BusinessException;

/**
 * Ordenação da listagem — campo + direção validados juntos, único ponto de parse da expressão
 * {@code "campo,direcao"} recebida em {@code ordenarPor} (D1). Nenhuma camada acima repete o
 * {@code split}.
 */
public record Ordenacao(CampoOrdenacao campo, DirecaoOrdenacao direcao) {

    /** Ordenação aplicada quando {@code ordenarPor} está ausente ou em branco — omissão válida. */
    public static Ordenacao padrao() {
        return new Ordenacao(CampoOrdenacao.DATA_CRIACAO, DirecaoOrdenacao.DESC);
    }

    /**
     * Parseia {@code "campo"} ou {@code "campo,direcao"} (D4): campo isolado usa {@code DESC};
     * campo vazio, direção vazia ou mais de duas partes são rejeitados — informar errado nunca é
     * omissão válida.
     */
    public static Ordenacao de(String expressao) {
        String[] partes = expressao.split(",", -1);
        if (partes.length > 2) {
            throw new BusinessException(String.format(
                    "Expressão de ordenação inválida: %s. Formato aceito: campo ou campo,direcao",
                    expressao));
        }

        CampoOrdenacao campo = mapearCampo(partes[0].trim());
        DirecaoOrdenacao direcao = partes.length == 2
                ? DirecaoOrdenacao.porNome(partes[1])
                : DirecaoOrdenacao.DESC;

        return new Ordenacao(campo, direcao);
    }

    /** Whitelist de aliases aceitos no request → vocabulário de domínio. Nada aqui conhece JPA. */
    private static CampoOrdenacao mapearCampo(String campoRequest) {
        return switch (campoRequest) {
            case "dataCriacao", "dataHoraInclusao" -> CampoOrdenacao.DATA_CRIACAO;
            case "valor", "valorAutorizacao" -> CampoOrdenacao.VALOR;
            case "idAutorizacao" -> CampoOrdenacao.ID_AUTORIZACAO;
            case "dataInicioVigencia" -> CampoOrdenacao.DATA_INICIO_VIGENCIA;
            case "dataFimVigencia" -> CampoOrdenacao.DATA_FIM_VIGENCIA;
            case "idPessoaRecebedora" -> CampoOrdenacao.ID_PESSOA_RECEBEDORA;
            case "status" -> CampoOrdenacao.STATUS;
            default -> throw new BusinessException(
                    String.format("Campo de ordenação inválido: %s. Campos aceitos: " +
                            "dataCriacao, dataHoraInclusao, valor, valorAutorizacao, idAutorizacao, " +
                            "dataInicioVigencia, dataFimVigencia, idPessoaRecebedora, status",
                            campoRequest));
        };
    }
}
