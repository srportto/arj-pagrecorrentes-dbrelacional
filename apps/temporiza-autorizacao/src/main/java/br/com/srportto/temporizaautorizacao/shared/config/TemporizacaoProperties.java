package br.com.srportto.temporizaautorizacao.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Parâmetros do agendamento/varredura/worker, com os defaults declarados em application.yaml. */
@ConfigurationProperties(prefix = "temporizacao")
public record TemporizacaoProperties(
        int prazoMinutos,
        long varreduraIntervaloMs,
        int varreduraLote,
        long streamMinIdleTimeMs,
        String chaveAgenda,
        String chaveStream,
        String grupoConsumidor,
        String consumidorId,
        String commandBaseUrl,
        long commandTimeoutMs,
        long consumidorOciosoLimiteMs) {

    public Duration prazo() {
        return Duration.ofMinutes(prazoMinutos);
    }
}
