package com.homeServer.server_dashboard.security;

import java.time.Clock;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.homeServer.server_dashboard.security.totp.TotpService.SecondFactorResult;
import com.homeServer.server_dashboard.service.TwoFactorService;

/**
 * Segundo passo do login. Ate aqui o usuario so' provou a senha: o estado pendente na sessao nao
 * carrega autoridade nenhuma, e nada e' gravado no {@code SecurityContext} antes de o codigo ser
 * aceito.
 */
@Controller
public class TwoFactorLoginController {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorLoginController.class);

    private static final String LOGIN_URL = "redirect:/login";
    private static final String ERROR_URL = "redirect:/login/2fa?error";
    private static final String EXPIRED_URL = "redirect:/login?expired";

    private final TwoFactorService twoFactorService;
    private final DashboardUserDetailsService userDetailsService;
    private final LoginAttemptService loginAttemptService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final Clock clock;

    public TwoFactorLoginController(TwoFactorService twoFactorService,
                                    DashboardUserDetailsService userDetailsService,
                                    LoginAttemptService loginAttemptService,
                                    Clock clock) {
        this.twoFactorService = twoFactorService;
        this.userDetailsService = userDetailsService;
        this.loginAttemptService = loginAttemptService;
        this.clock = clock;
    }

    @GetMapping("/login/2fa")
    public String showSecondFactorForm(HttpServletRequest request) {
        return resolvePending(request) == null ? LOGIN_URL : "home/login-2fa";
    }

    @PostMapping("/login/2fa")
    public String submitSecondFactor(@RequestParam(name = "code", required = false) String submittedCode,
                                     HttpServletRequest request, HttpServletResponse response) {
        PendingTwoFactorAuthentication pending = resolvePending(request);
        if (pending == null) {
            // Sessao pendente expirada ou de outro IP: volta ao inicio, sem dizer mais do que isso.
            return EXPIRED_URL;
        }

        SecondFactorResult result = twoFactorService.verifySecondFactor(pending.username(), submittedCode);
        if (!result.isAccepted()) {
            loginAttemptService.recordFailure(request.getRemoteAddr());
            log.warn("[ServerDash] segundo fator invalido para o usuario '{}' vindo do IP {}",
                    pending.username(), request.getRemoteAddr());
            return ERROR_URL;
        }

        return completeLogin(pending, request, response);
    }

    /**
     * Conclui a autenticacao: renova o id da sessao (o passo da senha nao autenticou, entao a
     * protecao contra fixacao de sessao do Spring Security nao chegou a rodar) e so' entao grava o
     * {@code Authentication} completo.
     */
    private String completeLogin(PendingTwoFactorAuthentication pending, HttpServletRequest request,
                                 HttpServletResponse response) {
        UserDetails user;
        try {
            user = userDetailsService.loadUserByUsername(pending.username());
        } catch (UsernameNotFoundException e) {
            return EXPIRED_URL;
        }
        if (!user.isEnabled()) {
            return EXPIRED_URL;
        }

        request.getSession().removeAttribute(PendingTwoFactorAuthentication.SESSION_ATTRIBUTE);
        request.changeSessionId();

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        loginAttemptService.recordSuccess(request.getRemoteAddr());
        log.info("[ServerDash] login concluido com segundo fator para o usuario '{}'", user.getUsername());
        return "redirect:/";
    }

    /**
     * @return o estado pendente se ele existe, ainda vale e veio do mesmo IP; caso contrario null,
     *         ja removendo o atributo obsoleto da sessao
     */
    private PendingTwoFactorAuthentication resolvePending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object attribute = session.getAttribute(PendingTwoFactorAuthentication.SESSION_ATTRIBUTE);
        if (!(attribute instanceof PendingTwoFactorAuthentication pending)) {
            return null;
        }
        if (!pending.isValidAt(Instant.now(clock), request.getRemoteAddr())) {
            session.removeAttribute(PendingTwoFactorAuthentication.SESSION_ATTRIBUTE);
            return null;
        }
        return pending;
    }
}
