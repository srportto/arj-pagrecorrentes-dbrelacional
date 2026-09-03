package br.com.srportto.contratocommand.infrastructure.web.contratosrest;

import br.com.srportto.contratocommand.application.TestFixtures;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre a faixa numérica de {@code quantidadeDividasCiclo}/{@code indicadorUsoLimiteConta} —
 * o teto impede o narrowing cast silencioso para {@code short} no modelo (design.md, D4).
 */
@DisplayName("Testes de Bean Validation de CriarAutorizacaoRequest")
class CriarAutorizacaoRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private CriarAutorizacaoRequest requestCom(Integer quantidadeDividasCiclo, Integer indicadorUsoLimiteConta) {
        CriarAutorizacaoRequest base = TestFixtures.criarRequestPix();
        return new CriarAutorizacaoRequest(
                base.dataFimVigencia(), base.tipoProduto(), base.valor(), base.idAutorizacaoEmpresa(),
                base.valorLimite(), base.frequencia(), quantidadeDividasCiclo, indicadorUsoLimiteConta,
                base.codigoCanalContratacao(), base.descricao(), base.idUnicoContaContratante(),
                base.idPessoaPagadora(), base.idPessoaDevedora(), base.idPessoaRecebedora(), base.metadados());
    }

    @Test
    @DisplayName("quantidadeDividasCiclo acima do limite do short é rejeitado")
    void quantidadeDividasCicloAcimaDoLimiteEhRejeitada() {
        Set<ConstraintViolation<CriarAutorizacaoRequest>> violacoes =
                validator.validate(requestCom(32768, 0));

        assertTrue(violacoes.stream().anyMatch(v -> "quantidadeDividasCiclo".equals(v.getPropertyPath().toString())));
    }

    @Test
    @DisplayName("quantidadeDividasCiclo no limite exato do short é aceito")
    void quantidadeDividasCicloNoLimiteEhAceita() {
        Set<ConstraintViolation<CriarAutorizacaoRequest>> violacoes =
                validator.validate(requestCom(32767, 0));

        assertTrue(violacoes.stream().noneMatch(v -> "quantidadeDividasCiclo".equals(v.getPropertyPath().toString())));
    }

    @Test
    @DisplayName("indicadorUsoLimiteConta fora do domínio booleano é rejeitado")
    void indicadorUsoLimiteContaForaDoDominioEhRejeitado() {
        Set<ConstraintViolation<CriarAutorizacaoRequest>> violacoes =
                validator.validate(requestCom(1, 2));

        assertTrue(violacoes.stream().anyMatch(v -> "indicadorUsoLimiteConta".equals(v.getPropertyPath().toString())));
    }

    @Test
    @DisplayName("valores válidos não geram violação nesses dois campos")
    void valoresValidosNaoGeramViolacao() {
        Set<ConstraintViolation<CriarAutorizacaoRequest>> violacoes = validator.validate(requestCom(1, 1));

        assertEquals(0, violacoes.stream()
                .filter(v -> "quantidadeDividasCiclo".equals(v.getPropertyPath().toString())
                        || "indicadorUsoLimiteConta".equals(v.getPropertyPath().toString()))
                .count());
    }
}
