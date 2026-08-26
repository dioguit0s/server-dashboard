package com.homeServer.server_dashboard.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * Traduz os eventos de autenticacao do Spring Security em contadores por IP no
 * {@link LoginAttemptService}.
 */
@Component
public class AuthenticationEventListener {

    private final LoginAttemptService loginAttemptService;

    public AuthenticationEventListener(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        String remoteAddress = resolveRemoteAddress(event.getAuthentication());
        if (remoteAddress != null) {
            loginAttemptService.recordFailure(remoteAddress);
        }
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String remoteAddress = resolveRemoteAddress(event.getAuthentication());
        if (remoteAddress != null) {
            loginAttemptService.recordSuccess(remoteAddress);
        }
    }

    /**
     * O IP vem dos detalhes preenchidos pelo {@code UsernamePasswordAuthenticationFilter}
     * ({@code request.getRemoteAddr()}), a mesma fonte usada pelo {@link LoginAttemptFilter}.
     */
    private String resolveRemoteAddress(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        return authentication.getDetails() instanceof WebAuthenticationDetails details
                ? details.getRemoteAddress()
                : null;
    }
}
