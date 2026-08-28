package com.homeServer.server_dashboard.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import com.homeServer.server_dashboard.repository.PersistentLoginTokenRepository;

/**
 * {@code PersistentTokenBasedRememberMeServices} padrao so' cancela o cookie no navegador durante o
 * logout — a serie continua valida no banco, entao um cookie ja capturado antes do logout ainda
 * autenticaria. Esta subclasse tambem apaga a serie usada, revogando o "lembrar de mim" daquele
 * dispositivo de verdade.
 */
public class RevokingPersistentTokenBasedRememberMeServices extends PersistentTokenBasedRememberMeServices {

    private final PersistentLoginTokenRepository tokenRepository;

    public RevokingPersistentTokenBasedRememberMeServices(String key, UserDetailsService userDetailsService,
                                                           PersistentTokenRepository persistentTokenRepository,
                                                           PersistentLoginTokenRepository tokenRepository) {
        super(key, userDetailsService, persistentTokenRepository);
        this.tokenRepository = tokenRepository;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String rememberMeCookie = extractRememberMeCookie(request);
        if (rememberMeCookie != null && !rememberMeCookie.isEmpty()) {
            try {
                String[] cookieTokens = decodeCookie(rememberMeCookie);
                if (cookieTokens.length == 2) {
                    tokenRepository.deleteById(cookieTokens[0]);
                }
            } catch (InvalidCookieException ex) {
                // Cookie ja invalido/corrompido: nada para revogar, so' segue para o cancelamento normal.
            }
        }
        super.logout(request, response, authentication);
    }
}
