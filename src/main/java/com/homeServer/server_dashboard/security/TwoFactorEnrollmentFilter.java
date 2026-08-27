package com.homeServer.server_dashboard.security;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Quando {@code dashboard.security.totp.required-for-admin} esta ligado, empurra todo ADMIN sem 2FA
 * para a tela de enrollment e nao o deixa usar o resto do dashboard antes de concluir.
 *
 * <p>Desligado por padrao: ligar isso em um dashboard cujo unico admin nao tenha como escanear o QR
 * no momento seria trancar o proprio dono para fora.
 */
@Component
public class TwoFactorEnrollmentFilter extends OncePerRequestFilter {

    static final String ENROLLMENT_URL = "/account/2fa";

    /**
     * Caminhos que continuam acessiveis durante o enrollment: a propria tela de conta (e sua API),
     * o logout e o que ja e' publico. Sem eles o redirecionamento entraria em laco.
     */
    private static final Set<String> ALWAYS_ALLOWED_PREFIXES = Set.of(
            "/account", "/api/account", "/logout", "/login", "/home/", "/css/", "/js/", "/icons/",
            "/ws/", "/error", "/favicon.ico", "/manifest.json", "/sw.js");

    private final boolean requiredForAdmin;

    public TwoFactorEnrollmentFilter(
            @Value("${dashboard.security.totp.required-for-admin:false}") boolean requiredForAdmin) {
        this.requiredForAdmin = requiredForAdmin;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (requiredForAdmin && needsEnrollment() && !isAllowedDuringEnrollment(request)) {
            denyUntilEnrolled(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean needsEnrollment() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof DashboardUserDetails user)) {
            return false;
        }
        boolean isAdmin = user.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return isAdmin && !user.isTotpEnabled();
    }

    /**
     * Navegacao e' desviada para a tela de enrollment; qualquer outro verbo e' recusado com 403,
     * porque um redirect em resposta a uma chamada de API viraria HTML no lugar de JSON — e deixar
     * passar transformaria a exigencia de 2FA em mera decoracao da interface.
     */
    private void denyUntilEnrolled(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (HttpMethod.GET.matches(request.getMethod())) {
            response.sendRedirect(request.getContextPath() + ENROLLMENT_URL);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"Ative o 2FA da sua conta para executar esta acao\"}");
    }

    private boolean isAllowedDuringEnrollment(HttpServletRequest request) {
        String path = pathWithinApplication(request);
        return ALWAYS_ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
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
