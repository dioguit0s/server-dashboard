package com.homeServer.server_dashboard.security;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Resolve as credenciais do admin em um unico ponto, ja no formato que o Spring Security espera
 * (hash com prefixo de algoritmo), para que nenhuma senha em texto puro fique guardada no
 * {@code UserDetails} depois da inicializacao.
 *
 * <p>Precedencia:
 * <ol>
 *   <li>{@code dashboard.admin.password-hash} (DASHBOARD_ADMIN_PASSWORD_HASH) — hash pronto,
 *       usado como esta;</li>
 *   <li>{@code dashboard.admin.password} (DASHBOARD_ADMIN_PASSWORD) — texto puro, convertido em
 *       hash BCrypt na inicializacao, com um WARN recomendando migrar para a variavel com hash.</li>
 * </ol>
 *
 * <p>Sem nenhuma das duas a aplicacao nao sobe: subir com senha vazia deixaria a area admin
 * acessivel com uma credencial que ninguem escolheu.
 */
public class AdminCredentials {

    private static final Logger log = LoggerFactory.getLogger(AdminCredentials.class);

    /**
     * Formato de um hash BCrypt sem prefixo de algoritmo, aceito por conveniencia porque e' o que
     * ferramentas externas (htpasswd, bibliotecas bcrypt) produzem.
     */
    private static final Pattern BCRYPT_HASH = Pattern.compile("\\A\\$2[aby]?\\$\\d\\d\\$[./0-9A-Za-z]{53}\\z");

    /**
     * Hash com prefixo de algoritmo, ex.: {@code {bcrypt}$2a$10$...}.
     */
    private static final Pattern PREFIXED_HASH = Pattern.compile("\\A\\{[a-zA-Z0-9+_-]+}.*", Pattern.DOTALL);

    private final String username;
    private final String encodedPassword;

    public AdminCredentials(String username, String plainPassword, String passwordHash, PasswordEncoder passwordEncoder) {
        if (isBlank(username)) {
            throw new IllegalStateException(
                    "Nenhum usuario admin configurado. Defina DASHBOARD_ADMIN_USERNAME (ou dashboard.admin.username).");
        }
        this.username = username.trim();
        this.encodedPassword = resolvePassword(plainPassword, passwordHash, passwordEncoder);
    }

    private static String resolvePassword(String plainPassword, String passwordHash, PasswordEncoder passwordEncoder) {
        if (!isBlank(passwordHash)) {
            return validatedHash(passwordHash.trim());
        }
        if (!isBlank(plainPassword)) {
            log.warn("DASHBOARD_ADMIN_PASSWORD esta definida em texto puro. A senha sera convertida em hash BCrypt "
                    + "na inicializacao, mas continua exposta no ambiente do processo. Gere um hash e use "
                    + "DASHBOARD_ADMIN_PASSWORD_HASH (veja o README).");
            return passwordEncoder.encode(plainPassword);
        }
        throw new IllegalStateException(
                "Nenhuma credencial de admin configurada. Defina DASHBOARD_ADMIN_PASSWORD_HASH (recomendado) "
                        + "ou DASHBOARD_ADMIN_PASSWORD antes de iniciar a aplicacao.");
    }

    /**
     * Um hash invalido so apareceria como "senha errada" no login, entao e' melhor recusar a
     * inicializacao com uma mensagem que diga o que esta errado.
     */
    private static String validatedHash(String passwordHash) {
        if (passwordHash.startsWith("{noop}")) {
            throw new IllegalStateException(
                    "DASHBOARD_ADMIN_PASSWORD_HASH esta com o prefixo {noop}, que guarda a senha em texto puro. "
                            + "Use um hash BCrypt; para senha em texto puro use DASHBOARD_ADMIN_PASSWORD.");
        }
        if (PREFIXED_HASH.matcher(passwordHash).matches()) {
            return passwordHash;
        }
        if (BCRYPT_HASH.matcher(passwordHash).matches()) {
            // Sem prefixo o DelegatingPasswordEncoder nao sabe qual algoritmo usar; assume-se BCrypt.
            return "{bcrypt}" + passwordHash;
        }
        throw new IllegalStateException(
                "DASHBOARD_ADMIN_PASSWORD_HASH nao parece um hash BCrypt valido (esperado algo como "
                        + "$2a$10$... ou {bcrypt}$2a$10$...). Veja o README para gerar o hash.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public String username() {
        return username;
    }

    /**
     * Senha ja em formato de hash com prefixo de algoritmo, pronta para o {@code UserDetails}.
     */
    public String encodedPassword() {
        return encodedPassword;
    }
}
