package com.homeServer.server_dashboard.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices;

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
            // O checkbox so' vem neste POST (o de /login/2fa nao o reenvia), entao precisa ser lido e
            // carregado no pendente agora para que o "lembrar de mim" seja aplicado so' depois que o
            // segundo fator for aceito (ver TwoFactorLoginController.completeLogin).
            PendingTwoFactorAuthentication pending = new PendingTwoFactorAuthentication(
                    twoFactorRequired.getUsername(), request.getRemoteAddr(), Instant.now(clock),
                    isRememberMeRequested(request));
            request.getSession(true).setAttribute(PendingTwoFactorAuthentication.SESSION_ATTRIBUTE, pending);
            getRedirectStrategy().sendRedirect(request, response, TWO_FACTOR_URL);
            return;
        }
        super.onAuthenticationFailure(request, response, exception);
    }

    private static boolean isRememberMeRequested(HttpServletRequest request) {
        String value = request.getParameter(AbstractRememberMeServices.DEFAULT_PARAMETER);
        return value != null
                && (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on")
                        || value.equalsIgnoreCase("yes") || value.equals("1"));
    }
}
