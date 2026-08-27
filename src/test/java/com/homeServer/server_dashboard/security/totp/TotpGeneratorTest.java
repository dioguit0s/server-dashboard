package com.homeServer.server_dashboard.security.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Vetores de teste da RFC 6238, apendice B, com o segredo HMAC-SHA1 de 20 bytes ("12345678901234567890").
 * Os codigos publicados na RFC tem 8 digitos; aqui comparamos os 6 finais, que sao os que o
 * gerador produz.
 */
class TotpGeneratorTest {

    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.UTF_8);

    @ParameterizedTest
    @CsvSource({
            "59,          287082",
            "1111111109,  081804",
            "1111111111,  050471",
            "1234567890,  005924",
            "2000000000,  279037",
            "20000000000, 353130"
    })
    void reproducesTheRfc6238Vectors(long epochSecond, String expectedCode) {
        assertEquals(expectedCode, TotpGenerator.generate(SECRET, TotpGenerator.timeStepAt(epochSecond)));
    }

    @Test
    void derivesTheTimeStepFromThirtySecondWindows() {
        assertEquals(0L, TotpGenerator.timeStepAt(0));
        assertEquals(0L, TotpGenerator.timeStepAt(29));
        assertEquals(1L, TotpGenerator.timeStepAt(30));
        assertEquals(1L, TotpGenerator.timeStepAt(59));
        assertEquals(2L, TotpGenerator.timeStepAt(60));
    }

    @Test
    void acceptsTheNeighbouringStepsWithinTheAllowedDrift() {
        long currentStep = TotpGenerator.timeStepAt(1111111109L);
        String previousCode = TotpGenerator.generate(SECRET, currentStep - 1);
        String nextCode = TotpGenerator.generate(SECRET, currentStep + 1);

        assertEquals(currentStep - 1, TotpGenerator.matchingTimeStep(SECRET, previousCode, currentStep, 1));
        assertEquals(currentStep + 1, TotpGenerator.matchingTimeStep(SECRET, nextCode, currentStep, 1));
    }

    @Test
    void rejectsStepsBeyondTheAllowedDrift() {
        long currentStep = TotpGenerator.timeStepAt(1111111109L);
        String farAwayCode = TotpGenerator.generate(SECRET, currentStep + 5);

        assertNull(TotpGenerator.matchingTimeStep(SECRET, farAwayCode, currentStep, 1));
    }

    @Test
    void acceptsCodesWithTheSeparatorsAuthenticatorAppsDisplay() {
        long currentStep = TotpGenerator.timeStepAt(59L);
        String code = TotpGenerator.generate(SECRET, currentStep);
        String spaced = code.substring(0, 3) + " " + code.substring(3);

        assertEquals(currentStep, TotpGenerator.matchingTimeStep(SECRET, spaced, currentStep, 1));
    }

    @Test
    void rejectsCodesWithTheWrongNumberOfDigits() {
        long currentStep = TotpGenerator.timeStepAt(59L);
        assertNull(TotpGenerator.matchingTimeStep(SECRET, "12345", currentStep, 1));
        assertNull(TotpGenerator.matchingTimeStep(SECRET, "1234567", currentStep, 1));
        assertNull(TotpGenerator.matchingTimeStep(SECRET, null, currentStep, 1));
    }
}
