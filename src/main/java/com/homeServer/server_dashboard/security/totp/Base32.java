package com.homeServer.server_dashboard.security.totp;

/**
 * Base32 do alfabeto padrao (RFC 4648), que e' como os aplicativos autenticadores esperam receber e
 * exibir o segredo TOTP.
 *
 * <p>Implementado aqui em vez de puxar uma dependencia: sao poucas linhas, e o projeto ja evita
 * bibliotecas externas no codigo de seguranca.
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int BITS_PER_CHARACTER = 5;
    private static final int BITS_PER_BYTE = 8;

    private Base32() {
    }

    /**
     * Codifica sem preenchimento ({@code =}) — os apps autenticadores aceitam, e o segredo fica
     * mais curto para quem precisar digitar a mao.
     */
    public static String encode(byte[] data) {
        StringBuilder encoded = new StringBuilder();
        int buffer = 0;
        int bitsInBuffer = 0;

        for (byte currentByte : data) {
            buffer = (buffer << BITS_PER_BYTE) | (currentByte & 0xFF);
            bitsInBuffer += BITS_PER_BYTE;
            while (bitsInBuffer >= BITS_PER_CHARACTER) {
                bitsInBuffer -= BITS_PER_CHARACTER;
                encoded.append(ALPHABET.charAt((buffer >>> bitsInBuffer) & 0x1F));
            }
        }

        if (bitsInBuffer > 0) {
            // Os bits que sobraram viram um ultimo caractere, completados com zeros a direita.
            encoded.append(ALPHABET.charAt((buffer << (BITS_PER_CHARACTER - bitsInBuffer)) & 0x1F));
        }
        return encoded.toString();
    }

    /**
     * Decodifica ignorando espacos, hifens, minusculas e o preenchimento {@code =}, porque o
     * segredo pode voltar digitado a mao.
     *
     * @throws IllegalArgumentException se aparecer um caractere fora do alfabeto Base32
     */
    public static byte[] decode(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("Segredo Base32 ausente");
        }

        java.io.ByteArrayOutputStream decoded = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsInBuffer = 0;

        for (char rawCharacter : encoded.toCharArray()) {
            if (rawCharacter == '=' || rawCharacter == ' ' || rawCharacter == '-') {
                continue;
            }
            int value = ALPHABET.indexOf(Character.toUpperCase(rawCharacter));
            if (value < 0) {
                throw new IllegalArgumentException("Caractere invalido em segredo Base32: " + rawCharacter);
            }
            buffer = (buffer << BITS_PER_CHARACTER) | value;
            bitsInBuffer += BITS_PER_CHARACTER;
            if (bitsInBuffer >= BITS_PER_BYTE) {
                bitsInBuffer -= BITS_PER_BYTE;
                decoded.write((buffer >>> bitsInBuffer) & 0xFF);
            }
        }
        return decoded.toByteArray();
    }
}
