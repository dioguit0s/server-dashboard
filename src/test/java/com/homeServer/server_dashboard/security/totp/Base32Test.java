package com.homeServer.server_dashboard.security.totp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Vetores de teste da RFC 4648, secao 10 — sem o preenchimento '=', que a implementacao omite.
 */
class Base32Test {

    @ParameterizedTest
    @CsvSource({
            "f,        MY",
            "fo,       MZXQ",
            "foo,      MZXW6",
            "foob,     MZXW6YQ",
            "fooba,    MZXW6YTB",
            "foobar,   MZXW6YTBOI"
    })
    void encodesTheRfc4648Vectors(String plain, String expectedBase32) {
        assertEquals(expectedBase32, Base32.encode(plain.getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @CsvSource({
            "MY,          f",
            "MZXQ,        fo",
            "MZXW6,       foo",
            "MZXW6YQ,     foob",
            "MZXW6YTB,    fooba",
            "MZXW6YTBOI,  foobar"
    })
    void decodesTheRfc4648Vectors(String base32, String expectedPlain) {
        assertArrayEquals(expectedPlain.getBytes(StandardCharsets.UTF_8), Base32.decode(base32));
    }

    @Test
    void decodeIgnoresPaddingSpacesHyphensAndCase() {
        byte[] expected = "foobar".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expected, Base32.decode("MZXW6YTBOI======"));
        assertArrayEquals(expected, Base32.decode("mzxw 6ytb-oi"));
    }

    @Test
    void rejectsCharactersOutsideTheAlphabet() {
        // '1' e '8' nao existem no alfabeto Base32 — um segredo digitado errado deve falhar alto,
        // e nao virar bytes silenciosamente diferentes.
        assertThrows(IllegalArgumentException.class, () -> Base32.decode("MZXW1YTB"));
        assertThrows(IllegalArgumentException.class, () -> Base32.decode("MZXW8YTB"));
    }

    @Test
    void roundTripsArbitraryBytes() {
        byte[] secret = new byte[20];
        for (int index = 0; index < secret.length; index++) {
            secret[index] = (byte) (index * 7 - 128);
        }
        assertArrayEquals(secret, Base32.decode(Base32.encode(secret)));
    }
}
