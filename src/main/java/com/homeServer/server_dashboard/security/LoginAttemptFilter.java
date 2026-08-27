package com.homeServer.server_dashboard.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Rejeita as submissoes de login vindas de IPs em bloqueio, antes que a tentativa chegue ao
 * {@code AuthenticationManager}. Como o pedido e' cortado aqui, nenhum evento de falha e'
 * publicado durante o bloqueio — a janela e' fixa a partir da ultima falha contabilizada.
 *
 * <p>Cobre os dois passos do login: a senha ({@code /login}) e o segundo fator
 * ({@code /login/2fa}). Sem o segundo, quem ja tivesse a senha poderia tentar codigos TOTP a
 * vontade, que e' exatamente o que o rate limiting existe para impedir.
 */
@Component
public class LoginAttemptFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptFilter.class);

    static final Set<String> LOGIN_PROCESSING_URLS = Set.of("/login", "/login/2fa");
    static final String BLOCKED_REDIRECT_URL = "/login?blocked";

    private final LoginAttemptService loginAttemptService;

    public LoginAttemptFilter(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isLoginSubmission(request)) {
            String remoteAddress = request.getRemoteAddr();
            if (loginAttemptService.isBlocked(remoteAddress)) {
                log.warn("[ServerDash] tentativa de login bloqueada — IP {} ainda em cooldown por {}s",
                        remoteAddress, loginAttemptService.remainingLockoutSeconds(remoteAddress));
                response.sendRedirect(request.getContextPath() + BLOCKED_REDIRECT_URL);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isLoginSubmission(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && LOGIN_PROCESSING_URLS.contains(pathWithinApplication(request));
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
