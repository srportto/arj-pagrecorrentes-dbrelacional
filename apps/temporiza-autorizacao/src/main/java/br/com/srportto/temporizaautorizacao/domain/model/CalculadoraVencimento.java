package br.com.srportto.temporizaautorizacao.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Regra de negócio única desta app: vencimento = {@code data_hora_inclusao} (do payload, não o
 * instante de consumo) + prazo. UTC explícito — o cálculo não pode depender do fuso da JVM que
 * processa a mensagem, potencialmente diferente entre réplicas/hosts.
 */
public final class CalculadoraVencimento {

    private CalculadoraVencimento() {
    }

    public static Instant calcular(LocalDateTime dataHoraInclusao, Duration prazo) {
        return dataHoraInclusao.plus(prazo).toInstant(ZoneOffset.UTC);
    }
}
