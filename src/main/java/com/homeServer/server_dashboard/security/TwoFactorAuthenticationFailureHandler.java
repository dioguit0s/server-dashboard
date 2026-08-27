package com.homeServer.server_dashboard.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

/**
 * Desvia o login para o segundo fator quando a senha estava certa, mantendo o comportamento antigo
 * ({@code /login?error}) para qualquer outra falha.
 */
public class TwoFactorAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    static final String TWO_FACTOR_URL = "/login/2fa";

    private final Clock clock;

    public TwoFactorAuthenticationFailureHandler(String defaultFailureUrl, Clock clock) {
        super(defaultFailureUrl);
        this.clock = clock;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        if (exception instanceof TwoFactorRequiredException twoFactorRequired) {
            PendingTwoFactorAuthentication pending = new PendingTwoFactorAuthentication(
                    twoFactorRequired.getUsername(), request.getRemoteAddr(), Instant.now(clock));
            request.getSession(true).setAttribute(PendingTwoFactorAuthentication.SESSION_ATTRIBUTE, pending);
            getRedirectStrategy().sendRedirect(request, response, TWO_FACTOR_URL);
            return;
        }
        super.onAuthenticationFailure(request, response, exception);
    }
}
