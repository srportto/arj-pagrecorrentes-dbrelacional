package br.com.srportto.contratocommand.application.enabledproduct.pixauto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do PixAutoMapper (impl gerado)")
class PixAutoMapperTest {

    private final PixAutoMapper mapper = new PixAutoMapperImpl();

    @Test
    @DisplayName("toDomain mapeia campos e o afterMapping aplica produto e inicializaCriacao")
    void toDomain() {
        Autorizacao aut = mapper.toDomain(TestFixtures.criarRequestPix());

        assertNotNull(aut);
        assertEquals(TipoProduto.PIX_AUTO, aut.getTipoProduto());
        assertEquals(0, aut.getValorAutorizacao().compareTo(new BigDecimal("1000.00")));
        assertEquals((short) 2, aut.getFrequenciaPagamento());
        assertNotNull(aut.getIdAutorizacao());
        assertNotNull(aut.getIdAutorizacao().getIdAutorizacao());
        assertEquals(1, aut.getStatus());
    }

    @Test
    @DisplayName("toDomain serializa metadados quando presentes")
    void toDomainComMetadado() {
        var meta = new ObjectMapper().readTree("{\"k\":\"v\"}");
        Autorizacao aut = mapper.toDomain(TestFixtures.criarRequest(
                "PIX_AUTO", new BigDecimal("10"), LocalDate.now().plusDays(1), meta));

        assertNotNull(aut.getMetadados());
        assertTrue(aut.getMetadados().contains("k"));
    }
}
