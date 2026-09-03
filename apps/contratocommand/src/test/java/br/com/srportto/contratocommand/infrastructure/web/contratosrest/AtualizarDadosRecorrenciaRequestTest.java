package br.com.srportto.contratocommand.infrastructure.web.contratosrest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre a faixa numérica de {@code quantidadeDividasCiclo}/{@code indicadorUsoLimiteConta} na
 * atualização parcial — {@code @Min}/{@code @Max} não podem quebrar a semântica de PATCH parcial
 * (campo ausente/{@code null} continua significando "não altera", design.md D3).
 */
@DisplayName("Testes de Bean Validation de AtualizarDadosRecorrenciaRequest")
class AtualizarDadosRecorrenciaRequestTest {

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

    private AtualizarDadosRecorrenciaRequest requestCom(Integer quantidadeDividasCiclo, Integer indicadorUsoLimiteConta) {
        return new AtualizarDadosRecorrenciaRequest(
                new BigDecimal("3000.00"), LocalDate.now().plusDays(60),
                indicadorUsoLimiteConta, quantidadeDividasCiclo, "C1", UUID.randomUUID());
    }

    @Test
    @DisplayName("quantidadeDividasCiclo acima do limite do short é rejeitado")
    void quantidadeDividasCicloAcimaDoLimiteEhRejeitada() {
        Set<ConstraintViolation<AtualizarDadosRecorrenciaRequest>> violacoes =
                validator.validate(requestCom(32768, 0));

        assertTrue(violacoes.stream().anyMatch(v -> "quantidadeDividasCiclo".equals(v.getPropertyPath().toString())));
    }

    @Test
    @DisplayName("indicadorUsoLimiteConta fora do domínio booleano é rejeitado")
    void indicadorUsoLimiteContaForaDoDominioEhRejeitado() {
        Set<ConstraintViolation<AtualizarDadosRecorrenciaRequest>> violacoes =
                validator.validate(requestCom(1, 2));

        assertTrue(violacoes.stream().anyMatch(v -> "indicadorUsoLimiteConta".equals(v.getPropertyPath().toString())));
    }

    @Test
    @DisplayName("campos ausentes (null) não disparam @Min/@Max — semântica de PATCH parcial preservada")
    void camposAusentesNaoDisparamConstraintsDeFaixa() {
        Set<ConstraintViolation<AtualizarDadosRecorrenciaRequest>> violacoes =
                validator.validate(requestCom(null, null));

        assertEquals(0, violacoes.stream()
                .filter(v -> "quantidadeDividasCiclo".equals(v.getPropertyPath().toString())
                        || "indicadorUsoLimiteConta".equals(v.getPropertyPath().toString()))
                .count());
    }

    @Test
    @DisplayName("valores válidos não geram violação nesses dois campos")
    void valoresValidosNaoGeramViolacao() {
        Set<ConstraintViolation<AtualizarDadosRecorrenciaRequest>> violacoes = validator.validate(requestCom(1, 1));

        assertEquals(0, violacoes.stream()
                .filter(v -> "quantidadeDividasCiclo".equals(v.getPropertyPath().toString())
                        || "indicadorUsoLimiteConta".equals(v.getPropertyPath().toString()))
                .count());
    }
}
