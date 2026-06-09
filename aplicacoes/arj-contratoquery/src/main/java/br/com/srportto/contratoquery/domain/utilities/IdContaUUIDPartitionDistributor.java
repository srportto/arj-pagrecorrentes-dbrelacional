package br.com.srportto.contratoquery.domain.utilities;

import java.math.BigInteger;
import java.util.UUID;

public class IdContaUUIDPartitionDistributor {

    public static int getPartitionFast(UUID uuid) {
        int hash = uuid.hashCode();
        return Math.abs(hash) % 889;
    }

    public static int getPartitionPrecision(UUID uuid) {
        String hex = uuid.toString().replace("-", "");
        BigInteger bigInt = new BigInteger(hex, 16);
        BigInteger divisor = new BigInteger("889");
        return bigInt.remainder(divisor).intValue();
    }
}
