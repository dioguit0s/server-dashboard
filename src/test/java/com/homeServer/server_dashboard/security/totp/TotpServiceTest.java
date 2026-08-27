package com.homeServer.server_dashboard.security.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.homeServer.server_dashboard.model.DashboardRole;
import com.homeServer.server_dashboard.model.DashboardUser;
import com.homeServer.server_dashboard.security.totp.TotpService.SecondFactorResult;

/**
 * Regras do segundo fator que nao aparecem nos vetores da RFC: anti-replay do codigo TOTP e uso
 * unico dos codigos de recuperacao.
 */
class TotpServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-27T12:00:00Z");

    /** BCrypt com custo minimo: o teste exercita a logica, nao a lentidao proposital do algoritmo. */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private TotpService totpService;
    private DashboardUser user;

    @BeforeEach
    void setUp() {
        totpService = new TotpService(passwordEncoder, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
        user = new DashboardUser("operador", "irrelevante", EnumSet.of(DashboardRole.VIEWER));
        user.setTotpSecret(totpService.generateSecret());
        user.setTotpEnabled(true);
    }

    @Test
    void acceptsTheCodeForTheCurrentTimeStep() {
        assertEquals(SecondFactorResult.TOTP, totpService.verifySecondFactor(user, currentCode()));
    }

    @Test
    void rejectsTheSameCodePresentedTwice() {
        String code = currentCode();
        assertEquals(SecondFactorResult.TOTP, totpService.verifySecondFactor(user, code));

        // Sem anti-replay, o codigo ainda valeria por ate mais 60 segundos.
        assertEquals(SecondFactorResult.REJECTED, totpService.verifySecondFactor(user, code));
    }

    @Test
    void rejectsAnEarlierCodeAfterALaterOneWasUsed() {
        long currentStep = TotpGenerator.timeStepAt(FIXED_NOW.getEpochSecond());
        String previousCode = TotpGenerator.generate(Base32.decode(user.getTotpSecret()), currentStep - 1);

        assertEquals(SecondFactorResult.TOTP, totpService.verifySecondFactor(user, currentCode()));
        assertEquals(SecondFactorResult.REJECTED, totpService.verifySecondFactor(user, previousCode));
    }

    @Test
    void acceptsTheNextCodeOnceTheClockMovesOn() {
        assertEquals(SecondFactorResult.TOTP, totpService.verifySecondFactor(user, currentCode()));

        Instant later = FIXED_NOW.plus(Duration.ofSeconds(60));
        TotpService laterService = new TotpService(passwordEncoder, Clock.fixed(later, ZoneOffset.UTC));
        String laterCode = TotpGenerator.generate(
                Base32.decode(user.getTotpSecret()), TotpGenerator.timeStepAt(later.getEpochSecond()));

        assertEquals(SecondFactorResult.TOTP, laterService.verifySecondFactor(user, laterCode));
    }

    @Test
    void rejectsAWrongCode() {
        assertEquals(SecondFactorResult.REJECTED, totpService.verifySecondFactor(user, "000000"));
    }

    @Test
    void rejectsAnyCodeWhenTwoFactorIsNotEnabled() {
        String code = currentCode();
        user.setTotpEnabled(false);

        assertEquals(SecondFactorResult.REJECTED, totpService.verifySecondFactor(user, code));
    }

    @Test
    void generatesTenRecoveryCodesThatOnlyWorkOnce() {
        List<String> recoveryCodes = totpService.regenerateRecoveryCodes(user);
        assertEquals(10, recoveryCodes.size());
        assertEquals(10, user.unusedRecoveryCodeCount());

        String firstCode = recoveryCodes.get(0);
        assertEquals(SecondFactorResult.RECOVERY_CODE, totpService.verifySecondFactor(user, firstCode));
        assertEquals(9, user.unusedRecoveryCodeCount());

        assertEquals(SecondFactorResult.REJECTED, totpService.verifySecondFactor(user, firstCode));
        assertEquals(SecondFactorResult.RECOVERY_CODE, totpService.verifySecondFactor(user, recoveryCodes.get(1)));
    }

    @Test
    void acceptsARecoveryCodeTypedWithoutTheHyphens() {
        List<String> recoveryCodes = totpService.regenerateRecoveryCodes(user);
        String typedWithoutHyphens = recoveryCodes.get(0).replace("-", "").toLowerCase(java.util.Locale.ROOT);

        assertEquals(SecondFactorResult.RECOVERY_CODE, totpService.verifySecondFactor(user, typedWithoutHyphens));
    }

    @Test
    void regeneratingInvalidatesThePreviousBatch() {
        String oldCode = totpService.regenerateRecoveryCodes(user).get(0);
        totpService.regenerateRecoveryCodes(user);

        assertEquals(SecondFactorResult.REJECTED, totpService.verifySecondFactor(user, oldCode));
    }

    @Test
    void neverStoresARecoveryCodeInClearText() {
        List<String> recoveryCodes = totpService.regenerateRecoveryCodes(user);
        String firstCode = recoveryCodes.get(0);

        user.getRecoveryCodes().forEach(stored -> assertNotEquals(firstCode, stored.getCodeHash()));
        assertTrue(passwordEncoder.matches(firstCode.replace("-", ""), user.getRecoveryCodes().get(0).getCodeHash()));
    }

    @Test
    void confirmsTheEnrollmentCodeAgainstAnUnsavedSecret() {
        String secret = totpService.generateSecret();
        String code = TotpGenerator.generate(
                Base32.decode(secret), TotpGenerator.timeStepAt(FIXED_NOW.getEpochSecond()));

        assertTrue(totpService.isValidForSecret(secret, code));
        assertFalse(totpService.isValidForSecret(secret, "000000"));
    }

    @Test
    void buildsAnOtpAuthUriTheAuthenticatorAppsUnderstand() {
        String uri = totpService.buildOtpAuthUri("operador", "JBSWY3DPEHPK3PXP");

        assertEquals("otpauth://totp/ServerDash%3Aoperador"
                + "?secret=JBSWY3DPEHPK3PXP&issuer=ServerDash&algorithm=SHA1&digits=6&period=30", uri);
    }

    private String currentCode() {
        return TotpGenerator.generate(
                Base32.decode(user.getTotpSecret()), TotpGenerator.timeStepAt(FIXED_NOW.getEpochSecond()));
    }
}
