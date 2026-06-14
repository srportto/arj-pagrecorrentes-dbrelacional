package br.com.srportto.contratoquery.domain.utilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do IdContaUUIDPartitionDistributor")
class IdContaUUIDPartitionDistributorTest {

    @Test
    @DisplayName("getPartitionFast retorna valor na faixa 0..888 e é determinístico")
    void partitionFastNaFaixaEDeterministico() {
        UUID uuid = UUID.randomUUID();
        int p1 = IdContaUUIDPartitionDistributor.getPartitionFast(uuid);
        int p2 = IdContaUUIDPartitionDistributor.getPartitionFast(uuid);
        assertEquals(p1, p2);
        assertTrue(p1 >= 0 && p1 < 889, "particao deve estar em 0..888, foi " + p1);
    }

    @Test
    @DisplayName("getPartitionPrecision retorna valor na faixa 0..888 e é determinístico")
    void partitionPrecisionNaFaixaEDeterministico() {
        UUID uuid = UUID.randomUUID();
        int p1 = IdContaUUIDPartitionDistributor.getPartitionPrecision(uuid);
        int p2 = IdContaUUIDPartitionDistributor.getPartitionPrecision(uuid);
        assertEquals(p1, p2);
        assertTrue(p1 >= 0 && p1 < 889, "particao deve estar em 0..888, foi " + p1);
    }

    @Test
    @DisplayName("ambas as estratégias se mantêm na faixa para várias contas")
    void variasContas() {
        for (int i = 0; i < 50; i++) {
            UUID uuid = UUID.randomUUID();
            assertTrue(IdContaUUIDPartitionDistributor.getPartitionFast(uuid) < 889);
            assertTrue(IdContaUUIDPartitionDistributor.getPartitionPrecision(uuid) < 889);
        }
    }
}
