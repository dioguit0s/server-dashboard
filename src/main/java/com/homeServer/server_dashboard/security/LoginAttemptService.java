package com.homeServer.server_dashboard.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting das tentativas de login: acumula falhas por IP de origem e bloqueia
 * temporariamente o IP depois de {@code maxAttempts} falhas, liberando quando a janela expira.
 *
 * <p>Como o IP efetivo vem do {@code X-Forwarded-For} (server.forward-headers-strategy=framework),
 * ele e' forjavel quando nao ha um reverse proxy confiavel na frente. Por isso existe tambem uma
 * trava global de reserva: um total de falhas vindas de qualquer IP tranca o login inteiro pela
 * mesma janela. Configure {@code global-max-attempts=0} para desativa-la.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /**
     * Falhas acumuladas de um IP. Imutavel: substituido por inteiro via {@code compute()}.
     */
    private record Attempt(int failures, Instant lastFailure, Instant blockedUntil) {

        boolean isBlockedAt(Instant now) {
            return blockedUntil != null && now.isBefore(blockedUntil);
        }

        /**
         * Um registro bloqueado expira ao fim do bloqueio; um registro apenas com falhas
         * parciais expira uma janela depois da ultima falha (contagem deslizante).
         */
        boolean isExpiredAt(Instant now, Duration window) {
            Instant expiresAt = blockedUntil != null ? blockedUntil : lastFailure.plus(window);
            return !now.isBefore(expiresAt);
        }
    }

    private final Map<String, Attempt> attemptsByAddress = new ConcurrentHashMap<>();
    private final AtomicInteger globalFailures = new AtomicInteger();
    private volatile Instant globalBlockedUntil;

    private final int maxAttempts;
    private final int globalMaxAttempts;
    private final int maxTrackedAddresses;
    private final Duration lockoutDuration;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(
            @Value("${dashboard.security.login.max-attempts:5}") int maxAttempts,
            @Value("${dashboard.security.login.lockout-minutes:15}") long lockoutMinutes,
            @Value("${dashboard.security.login.global-max-attempts:25}") int globalMaxAttempts,
            @Value("${dashboard.security.login.max-tracked-ips:10000}") int maxTrackedAddresses) {
        this(maxAttempts, lockoutMinutes, globalMaxAttempts, maxTrackedAddresses, Clock.systemUTC());
    }

    LoginAttemptService(int maxAttempts, long lockoutMinutes, int globalMaxAttempts,
                        int maxTrackedAddresses, Clock clock) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.lockoutDuration = Duration.ofMinutes(Math.max(1, lockoutMinutes));
        this.globalMaxAttempts = Math.max(0, globalMaxAttempts);
        this.maxTrackedAddresses = Math.max(1, maxTrackedAddresses);
        this.clock = clock;
    }

    /**
     * @return true se o IP (ou o login como um todo) esta bloqueado neste instante.
     */
    public boolean isBlocked(String remoteAddress) {
        Instant now = clock.instant();
        if (isGloballyBlockedAt(now)) {
            return true;
        }
        Attempt attempt = attemptsByAddress.get(normalize(remoteAddress));
        return attempt != null && attempt.isBlockedAt(now);
    }

    /**
     * Segundos restantes do bloqueio — apenas para log/diagnostico.
     */
    public long remainingLockoutSeconds(String remoteAddress) {
        Instant now = clock.instant();
        Instant until = null;
        Attempt attempt = attemptsByAddress.get(normalize(remoteAddress));
        if (attempt != null && attempt.isBlockedAt(now)) {
            until = attempt.blockedUntil();
        }
        Instant global = globalBlockedUntil;
        if (global != null && now.isBefore(global) && (until == null || global.isAfter(until))) {
            until = global;
        }
        return until == null ? 0 : Duration.between(now, until).toSeconds();
    }

    /**
     * Registra uma tentativa de autenticacao malsucedida vinda do IP informado.
     */
    public void recordFailure(String remoteAddress) {
        String address = normalize(remoteAddress);
        Instant now = clock.instant();

        if (!attemptsByAddress.containsKey(address)) {
            ensureCapacityFor(now);
        }

        Attempt updated = attemptsByAddress.compute(address, (key, current) -> {
            if (current == null || current.isExpiredAt(now, lockoutDuration)) {
                current = new Attempt(0, now, null);
            }
            int failures = current.failures() + 1;
            Instant blockedUntil = failures >= maxAttempts ? now.plus(lockoutDuration) : current.blockedUntil();
            return new Attempt(failures, now, blockedUntil);
        });

        if (updated.failures() == maxAttempts) {
            log.warn("[ServerDash] login bloqueado para o IP {} apos {} falhas — liberado em {} min",
                    address, updated.failures(), lockoutDuration.toMinutes());
        } else if (updated.failures() > maxAttempts) {
            log.warn("[ServerDash] nova falha de login do IP ja bloqueado {} (total={})",
                    address, updated.failures());
        }

        registerGlobalFailure(now);
    }

    /**
     * Login bem-sucedido: zera o contador do IP e a trava global.
     */
    public void recordSuccess(String remoteAddress) {
        attemptsByAddress.remove(normalize(remoteAddress));
        globalFailures.set(0);
        globalBlockedUntil = null;
    }

    /**
     * Remove periodicamente os registros cuja janela ja expirou, evitando crescimento indefinido.
     */
    @Scheduled(fixedDelay = 300_000)
    public void purgeExpired() {
        Instant now = clock.instant();
        attemptsByAddress.values().removeIf(attempt -> attempt.isExpiredAt(now, lockoutDuration));
        Instant global = globalBlockedUntil;
        if (global != null && !now.isBefore(global)) {
            globalBlockedUntil = null;
            globalFailures.set(0);
        }
    }

    int trackedAddressCount() {
        return attemptsByAddress.size();
    }

    private boolean isGloballyBlockedAt(Instant now) {
        Instant until = globalBlockedUntil;
        return until != null && now.isBefore(until);
    }

    private void registerGlobalFailure(Instant now) {
        if (globalMaxAttempts == 0) {
            return;
        }
        if (isGloballyBlockedAt(now)) {
            return;
        }
        if (globalFailures.incrementAndGet() >= globalMaxAttempts) {
            globalBlockedUntil = now.plus(lockoutDuration);
            globalFailures.set(0);
            log.warn("[ServerDash] trava global do login ativada apos {} falhas de multiplos IPs — liberada em {} min",
                    globalMaxAttempts, lockoutDuration.toMinutes());
        }
    }

    /**
     * Mantem o mapa limitado: primeiro descarta expirados, depois remove o registro mais antigo.
     * O IP e' controlado pelo cliente (X-Forwarded-For), entao o mapa nao pode crescer sem limite.
     */
    private void ensureCapacityFor(Instant now) {
        if (attemptsByAddress.size() < maxTrackedAddresses) {
            return;
        }
        attemptsByAddress.values().removeIf(attempt -> attempt.isExpiredAt(now, lockoutDuration));
        while (attemptsByAddress.size() >= maxTrackedAddresses) {
            String oldest = attemptsByAddress.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().lastFailure()))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldest == null) {
                return;
            }
            attemptsByAddress.remove(oldest);
        }
    }

    private static String normalize(String remoteAddress) {
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }
}
