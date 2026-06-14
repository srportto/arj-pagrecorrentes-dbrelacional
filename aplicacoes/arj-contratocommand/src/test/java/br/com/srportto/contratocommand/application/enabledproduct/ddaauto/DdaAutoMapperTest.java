package br.com.srportto.contratocommand.application.enabledproduct.ddaauto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do DdaAutoMapper (impl gerado)")
class DdaAutoMapperTest {

    private final DdaAutoMapper mapper = new DdaAutoMapperImpl();

    @Test
    @DisplayName("toDomain mapeia campos e o afterMapping aplica produto e inicializaCriacao")
    void toDomain() {
        Autorizacao aut = mapper.toDomain(TestFixtures.criarRequestDda());

        assertNotNull(aut);
        assertEquals(TipoProduto.DDA_AUTO, aut.getTipoProduto());
        assertEquals(0, aut.getValorAutorizacao().compareTo(new BigDecimal("1000.00")));
        assertNotNull(aut.getIdAutorizacao());
        assertNotNull(aut.getIdAutorizacao().getIdAutorizacao());
        assertEquals(1, aut.getStatus());
    }
}
