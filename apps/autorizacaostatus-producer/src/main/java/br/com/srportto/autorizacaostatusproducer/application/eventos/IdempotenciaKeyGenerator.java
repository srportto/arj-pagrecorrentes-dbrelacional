package br.com.srportto.autorizacaostatusproducer.application.eventos;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Gera a key de idempotencia da mensagem Kafka: SHA-256 de id_autorizacao + data/hora
 * da ultima atualizacao, calculado a partir dos campos tipados (nunca da string JSON
 * crua) com formatter fixo, para que reentregas da mesma transicao de estado produzam
 * sempre a mesma key.
 */
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
