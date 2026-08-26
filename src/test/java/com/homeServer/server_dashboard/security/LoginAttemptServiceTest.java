package com.homeServer.server_dashboard.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    private static final String IP = "203.0.113.10";
    private static final String OTHER_IP = "203.0.113.11";
    private static final int MAX_ATTEMPTS = 3;
    private static final long LOCKOUT_MINUTES = 15;

    /**
     * Relogio controlado pelo teste: permite avancar o tempo sem Thread.sleep.
     */
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }
    }

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        service = new LoginAttemptService(MAX_ATTEMPTS, LOCKOUT_MINUTES, 0, 10_000, clock);
    }

    @Test
    void blocksAddressAfterConfiguredNumberOfFailures() {
        for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) {
            service.recordFailure(IP);
            assertFalse(service.isBlocked(IP), "nao deve bloquear antes de " + MAX_ATTEMPTS + " falhas");
        }

        service.recordFailure(IP);

        assertTrue(service.isBlocked(IP));
        assertTrue(service.remainingLockoutSeconds(IP) > 0);
    }

    @Test
    void doesNotBlockOtherAddresses() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            service.recordFailure(IP);
        }

        assertTrue(service.isBlocked(IP));
        assertFalse(service.isBlocked(OTHER_IP));
    }

    @Test
    void releasesAddressAfterLockoutWindowExpires() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            service.recordFailure(IP);
        }
        assertTrue(service.isBlocked(IP));

        clock.advance(Duration.ofMinutes(LOCKOUT_MINUTES - 1));
        assertTrue(service.isBlocked(IP), "ainda dentro da janela");

        clock.advance(Duration.ofMinutes(1));
        assertFalse(service.isBlocked(IP), "janela expirada libera o IP");
        assertEquals(0, service.remainingLockoutSeconds(IP));
    }

    @Test
    void countingRestartsAfterWindowExpires() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            service.recordFailure(IP);
        }
        clock.advance(Duration.ofMinutes(LOCKOUT_MINUTES));

        service.recordFailure(IP);

        assertFalse(service.isBlocked(IP), "a contagem recomeca do zero apos a expiracao");
    }

    @Test
    void successfulLoginResetsTheCounter() {
        service.recordFailure(IP);
        service.recordFailure(IP);

        service.recordSuccess(IP);
        service.recordFailure(IP);

        assertFalse(service.isBlocked(IP), "o contador foi zerado pelo login bem-sucedido");
    }

    @Test
    void successfulLoginClearsAnActiveBlock() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            service.recordFailure(IP);
        }
        assertTrue(service.isBlocked(IP));

        service.recordSuccess(IP);

        assertFalse(service.isBlocked(IP));
    }

    @Test
    void globalLockCatchesFailuresSpreadAcrossManyAddresses() {
        int globalMax = 6;
        service = new LoginAttemptService(MAX_ATTEMPTS, LOCKOUT_MINUTES, globalMax, 10_000, clock);

        for (int i = 0; i < globalMax; i++) {
            service.recordFailure("198.51.100." + i);
        }

        assertTrue(service.isBlocked("198.51.100.200"), "IP nunca visto tambem e' barrado pela trava global");

        clock.advance(Duration.ofMinutes(LOCKOUT_MINUTES));
        assertFalse(service.isBlocked("198.51.100.200"), "trava global expira junto com a janela");
    }

    @Test
    void globalLockIsDisabledWhenLimitIsZero() {
        for (int i = 0; i < 50; i++) {
            service.recordFailure("198.51.100." + i);
        }

        assertFalse(service.isBlocked("203.0.113.99"));
    }

    @Test
    void successfulLoginClearsTheGlobalLock() {
        int globalMax = 4;
        service = new LoginAttemptService(MAX_ATTEMPTS, LOCKOUT_MINUTES, globalMax, 10_000, clock);
        for (int i = 0; i < globalMax; i++) {
            service.recordFailure("198.51.100." + i);
        }
        assertTrue(service.isBlocked(OTHER_IP));

        service.recordSuccess(OTHER_IP);

        assertFalse(service.isBlocked(OTHER_IP));
    }

    @Test
    void trackedAddressesNeverExceedTheConfiguredCap() {
        int cap = 10;
        service = new LoginAttemptService(MAX_ATTEMPTS, LOCKOUT_MINUTES, 0, cap, clock);

        for (int i = 0; i < cap * 5; i++) {
            service.recordFailure("198.51.100." + i);
        }

        assertTrue(service.trackedAddressCount() <= cap,
                "o mapa de IPs nao pode crescer sem limite (X-Forwarded-For e' forjavel)");
    }

    @Test
    void purgeExpiredKeepsPartialCountersInsideTheWindow() {
        service.recordFailure(IP);
        service.recordFailure(IP);

        service.purgeExpired();
        service.recordFailure(IP);

        assertTrue(service.isBlocked(IP), "a limpeza periodica nao pode zerar contagens recentes");
    }

    @Test
    void purgeExpiredDropsRecordsOutsideTheWindow() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            service.recordFailure(IP);
        }
        assertEquals(1, service.trackedAddressCount());

        clock.advance(Duration.ofMinutes(LOCKOUT_MINUTES));
        service.purgeExpired();

        assertEquals(0, service.trackedAddressCount());
    }
}
