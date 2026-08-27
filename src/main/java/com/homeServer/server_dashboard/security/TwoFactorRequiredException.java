package com.homeServer.server_dashboard.security;

import org.springframework.security.core.AuthenticationException;

/**
 * Sinaliza que a senha estava correta mas o login ainda nao terminou: falta o segundo fator.
 *
 * <p>E' uma {@link AuthenticationException} de proposito. Se o passo da senha publicasse um
 * {@code AuthenticationSuccessEvent}, o {@link AuthenticationEventListener} zeraria o contador de
 * tentativas do IP e quem ja tivesse a senha poderia tentar codigos TOTP indefinidamente, sem
 * lockout. Interrompendo a autenticacao aqui, o contador nao e' zerado — e, como esta excecao nao
 * esta mapeada no {@code DefaultAuthenticationEventPublisher}, tambem nao e' contabilizada como
 * falha de senha, que seria injusto com quem acertou a senha.
 */
public class TwoFactorRequiredException extends AuthenticationException {

    private final String username;

    public TwoFactorRequiredException(String username) {
        super("Segundo fator necessario para concluir o login");
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
