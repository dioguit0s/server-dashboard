package com.homeServer.server_dashboard.security.totp;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP conforme a RFC 6238 (que por sua vez usa o HOTP da RFC 4226): HMAC-SHA1, 6 digitos e passo
 * de 30 segundos — os padroes que Google Authenticator, Aegis, 1Password e afins assumem.
 */
public final class TotpGenerator {

    public static final Duration TIME_STEP = Duration.ofSeconds(30);
    public static final int DIGITS = 6;

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int[] POWERS_OF_TEN = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000};

    private TotpGenerator() {
    }

    /**
     * Numero do passo de tempo a que um instante pertence.
     */
    public static long timeStepAt(long epochSecond) {
        return Math.floorDiv(epochSecond, TIME_STEP.toSeconds());
    }

    /**
     * Codigo de {@value #DIGITS} digitos para um passo de tempo, ja com zeros a esquerda.
     */
    public static String generate(byte[] secret, long timeStep) {
        byte[] hash = hmacSha1(secret, ByteBuffer.allocate(Long.BYTES).putLong(timeStep).array());

        // Truncamento dinamico da RFC 4226: os 4 bits finais escolhem onde comecar a ler.
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

        int code = binary % POWERS_OF_TEN[DIGITS];
        return String.format(Locale.ROOT, "%0" + DIGITS + "d", code);
    }

    /**
     * Confere um codigo digitado contra os passos de {@code timeStep - allowedDrift} ate
     * {@code timeStep + allowedDrift}, tolerando relogios levemente dessincronizados.
     *
     * @return o passo de tempo que casou, ou {@code null} se nenhum casou
     */
    public static Long matchingTimeStep(byte[] secret, String submittedCode, long timeStep, int allowedDrift) {
        String normalized = normalize(submittedCode);
        if (normalized.length() != DIGITS) {
            return null;
        }
        for (long candidate = timeStep - allowedDrift; candidate <= timeStep + allowedDrift; candidate++) {
            if (constantTimeEquals(generate(secret, candidate), normalized)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Aceita o codigo com os espacos que os apps costumam exibir ("123 456").
     */
    private static String normalize(String submittedCode) {
        return submittedCode == null ? "" : submittedCode.replaceAll("[\\s-]", "");
    }

    /**
     * Comparacao sem vazar, pelo tempo de resposta, quantos digitos iniciais estavam certos.
     */
    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] hmacSha1(byte[] secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Falha ao calcular o HMAC do codigo TOTP", e);
        }
    }
}
