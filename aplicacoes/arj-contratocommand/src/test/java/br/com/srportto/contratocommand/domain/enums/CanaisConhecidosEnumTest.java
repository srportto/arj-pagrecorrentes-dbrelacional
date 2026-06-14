package br.com.srportto.contratocommand.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do enum CanaisConhecidosEnum")
class CanaisConhecidosEnumTest {

    @Test
    @DisplayName("obterCanalEnumPorIdCanal resolve todos os códigos e expõe descrição")
    void obtemPorCodigoParaTodos() {
        for (CanaisConhecidosEnum canal : CanaisConhecidosEnum.values()) {
            CanaisConhecidosEnum encontrado =
                    CanaisConhecidosEnum.obterCanalEnumPorIdCanal(canal.getCodigoCanal());
            assertEquals(canal, encontrado);
            assertNotNull(encontrado.getDescricao());
        }
    }

    @Test
    @DisplayName("obterCanalEnumPorIdCanal é case-insensitive")
    void caseInsensitive() {
        assertEquals(CanaisConhecidosEnum.MB1, CanaisConhecidosEnum.obterCanalEnumPorIdCanal("mb1"));
    }

    @Test
    @DisplayName("obterCanalEnumPorIdCanal lança IllegalArgumentException para código desconhecido")
    void lancaParaDesconhecido() {
        assertThrows(IllegalArgumentException.class,
                () -> CanaisConhecidosEnum.obterCanalEnumPorIdCanal("ZZ9"));
    }
}
