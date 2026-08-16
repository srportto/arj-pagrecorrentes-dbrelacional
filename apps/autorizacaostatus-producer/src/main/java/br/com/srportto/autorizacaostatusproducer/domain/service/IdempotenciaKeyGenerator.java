package br.com.srportto.autorizacaostatusproducer.domain.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/** Gera a key Kafka de idempotência: SHA-256(id_autorizacao + data_hora_ultima_atlz) a partir dos campos tipados, nunca da string JSON crua. */
@Component
public class IdempotenciaKeyGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public String gerar(UUID idAutorizacao, LocalDateTime dataHoraUltimaAtualizacao) {
        String base = idAutorizacao.toString() + FORMATTER.format(dataHoraUltimaAtualizacao);
        return HexFormat.of().formatHex(sha256(base));
    }

    private byte[] sha256(String valor) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

}
