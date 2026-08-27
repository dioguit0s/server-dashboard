package com.homeServer.server_dashboard.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * Envolve a autenticacao por senha e, quando o usuario tem 2FA ligado, recusa o token completo:
 * a autenticacao so' se conclui depois do segundo fator, em {@code POST /login/2fa}.
 *
 * <p>Fazer isso dentro do provider — e nao em um {@code AuthenticationSuccessHandler} — garante que
 * nenhum {@code Authentication} com as autoridades reais chegue ao {@code SecurityContext} antes do
 * segundo fator, nem mesmo por um instante.
 */
public class TwoFactorAuthenticationProvider implements AuthenticationProvider {

    private final AuthenticationProvider delegate;

    public TwoFactorAuthenticationProvider(AuthenticationProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Authentication authenticated = delegate.authenticate(authentication);

        if (authenticated != null
                && authenticated.getPrincipal() instanceof DashboardUserDetails user
                && user.isTotpEnabled()) {
            throw new TwoFactorRequiredException(user.getUsername());
        }
        return authenticated;
    }

    @Override
    public boolean supports(Class<?> authenticationType) {
        return delegate.supports(authenticationType);
    }
}
