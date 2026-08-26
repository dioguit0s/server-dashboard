package com.homeServer.server_dashboard.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolve as origens aceitas no handshake SockJS/STOMP e recusa o curinga em ambientes que nao
 * sejam de desenvolvimento.
 *
 * <p>Com um curinga, qualquer pagina consegue abrir o handshake e o navegador da vitima envia junto
 * o cookie de sessao: uma aba maliciosa aberta por um admin autenticado passa a ler os topicos
 * protegidos (containers, processos, servicos). Como o CSRF e' desabilitado em {@code /ws/**} —
 * normal para STOMP —, o controle por origem e' a unica barreira nessa etapa.
 *
 * <p>Por isso o curinga so' sobrevive a inicializacao em dois casos:
 * <ul>
 *   <li>um perfil de desenvolvimento esta ativo ({@link #DEVELOPMENT_PROFILES}); ou</li>
 *   <li>alguem assumiu o risco explicitamente com
 *       {@code dashboard.websocket.allow-wildcard-origins} (DASHBOARD_WS_ALLOW_WILDCARD).</li>
 * </ul>
 * Nos dois casos fica um {@code WARN} na inicializacao. Fora deles a aplicacao nao sobe, para que um
 * deploy real nao herde o default permissivo por descuido.
 */
public final class WebSocketAllowedOrigins {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAllowedOrigins.class);

    /**
     * Perfis tratados como desenvolvimento. Sem nenhum perfil ativo o Spring usa {@code default},
     * que nao esta na lista: a postura de producao e' o padrao justamente porque um deploy raro
     * lembra de declarar que e' producao.
     */
    private static final Set<String> DEVELOPMENT_PROFILES = Set.of("dev", "development", "local", "test");

    private static final String WILDCARD = "*";

    /** Prefixos de esquema irrelevantes para decidir se o host e' curinga. */
    private static final List<String> SCHEME_PREFIXES = List.of("http://", "https://", "ws://", "wss://", "*://", "//");

    private final String[] patterns;

    public WebSocketAllowedOrigins(String rawPatterns, Collection<String> activeProfiles, boolean wildcardExplicitlyAllowed) {
        String[] parsed = parse(rawPatterns);
        // Sem nada configurado o comportamento equivale ao curinga, entao passa pelo mesmo guarda.
        this.patterns = parsed.length == 0 ? new String[]{WILDCARD} : parsed;
        guardAgainstWildcard(this.patterns, activeProfiles, wildcardExplicitlyAllowed);
    }

    /**
     * Origens no formato aceito por {@code setAllowedOriginPatterns}.
     */
    public String[] patterns() {
        return patterns.clone();
    }

    public boolean hasWildcard() {
        return Arrays.stream(patterns).anyMatch(WebSocketAllowedOrigins::isWildcard);
    }

    private static String[] parse(String rawPatterns) {
        if (rawPatterns == null) {
            return new String[0];
        }
        return Arrays.stream(rawPatterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toArray(String[]::new);
    }

    private static void guardAgainstWildcard(String[] patterns, Collection<String> activeProfiles,
                                             boolean wildcardExplicitlyAllowed) {
        String wildcard = Arrays.stream(patterns)
                .filter(WebSocketAllowedOrigins::isWildcard)
                .findFirst()
                .orElse(null);
        if (wildcard == null) {
            return;
        }
        if (wildcardExplicitlyAllowed) {
            log.warn("[ServerDash] WebSocket aceitando qualquer origem ('{}') porque "
                    + "dashboard.websocket.allow-wildcard-origins (DASHBOARD_WS_ALLOW_WILDCARD) esta ligado. "
                    + "Qualquer pagina aberta por um admin autenticado consegue abrir o handshake SockJS/STOMP "
                    + "com o cookie de sessao dele e ler os topicos protegidos. Prefira listar as origens do "
                    + "dashboard em DASHBOARD_WS_ORIGINS.", wildcard);
            return;
        }
        if (isDevelopment(activeProfiles)) {
            log.warn("[ServerDash] WebSocket aceitando qualquer origem ('{}'). Aceito aqui porque um perfil de "
                    + "desenvolvimento esta ativo ({}), mas em producao qualquer pagina aberta por um admin "
                    + "autenticado conseguiria abrir o handshake SockJS/STOMP com o cookie de sessao dele e ler "
                    + "os topicos protegidos. Defina DASHBOARD_WS_ORIGINS antes de expor a aplicacao.",
                    wildcard, activeProfiles);
            return;
        }
        throw new IllegalStateException(
                "dashboard.websocket.allowed-origin-patterns esta com o curinga '" + wildcard
                        + "', que aceita o handshake SockJS/STOMP de qualquer origem — inclusive de uma pagina "
                        + "maliciosa aberta por um admin autenticado, que enviaria junto o cookie de sessao dele. "
                        + "Defina DASHBOARD_WS_ORIGINS com as origens do dashboard "
                        + "(ex.: https://meudominio.com,http://localhost:8080). Em desenvolvimento, rode com o "
                        + "perfil dev (SPRING_PROFILES_ACTIVE=dev); para manter o curinga conscientemente, ligue "
                        + "DASHBOARD_WS_ALLOW_WILDCARD=true.");
    }

    private static boolean isDevelopment(Collection<String> activeProfiles) {
        return activeProfiles != null && activeProfiles.stream()
                .filter(profile -> profile != null)
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch(DEVELOPMENT_PROFILES::contains);
    }

    /**
     * Curinga e' todo padrao cujo host casa com qualquer coisa — {@code *}, {@code http://*},
     * {@code *://*}, {@code https://*:*}. Um padrao de subdominio ({@code https://*.dominio.com})
     * continua sendo uma restricao real e nao e' sinalizado.
     */
    static boolean isWildcard(String pattern) {
        if (pattern == null) {
            return false;
        }
        String host = pattern.trim().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) {
            return false;
        }
        for (String scheme : SCHEME_PREFIXES) {
            if (host.startsWith(scheme)) {
                host = host.substring(scheme.length());
                break;
            }
        }
        int port = host.lastIndexOf(':');
        if (port >= 0) {
            host = host.substring(0, port);
        }
        return WILDCARD.equals(host);
    }
}
