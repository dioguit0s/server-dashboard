package com.homeServer.server_dashboard.security;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * Estado guardado na sessao entre o acerto da senha e o segundo fator.
 *
 * <p>Nao carrega autoridade nenhuma: e' so' a anotacao de quem acertou a senha, de onde, e quando.
 * Vale por {@link #VALIDITY} e esta presa ao IP de origem, para que a sessao meio-autenticada nao
 * seja util a mais ninguem.
 */
public record PendingTwoFactorAuthentication(String username, String remoteAddress, Instant createdAt)
        implements Serializable {

    public static final String SESSION_ATTRIBUTE = "SERVERDASH_PENDING_2FA";

    /** Tempo para pegar o celular e digitar o codigo, sem deixar a sessao pendente aberta a toa. */
    public static final Duration VALIDITY = Duration.ofMinutes(5);

    public boolean isValidAt(Instant now, String currentRemoteAddress) {
        return now.isBefore(createdAt.plus(VALIDITY))
                && remoteAddress != null
                && remoteAddress.equals(currentRemoteAddress);
    }
}
