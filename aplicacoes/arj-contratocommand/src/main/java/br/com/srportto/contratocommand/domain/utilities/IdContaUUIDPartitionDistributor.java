package br.com.srportto.contratocommand.domain.utilities;

import java.math.BigInteger;
import java.util.UUID;

public class IdContaUUIDPartitionDistributor {

  // Metodo ultra rápido (bom o suficiente para a maioria dos casos)
    public static int getPartitionFast(UUID uuid) {
        // Pega o hashCode (32 bits), garante que é positivo e tira o módulo
        int hash = uuid.hashCode();
        return Math.abs(hash) % 889;
    }
    
    // Metodo garantido (matematicamente perfeito distribuindo os 128 bits)
    public static int getPartitionPrecision(UUID uuid) {
        String hex = uuid.toString().replace("-", "");
        BigInteger bigInt = new BigInteger(hex, 16);
        BigInteger divisor = new BigInteger("889");
        return bigInt.remainder(divisor).intValue();
    }

}
