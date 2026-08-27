package com.homeServer.server_dashboard.model;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Papeis do dashboard, do menos para o mais permissivo.
 *
 * <p>{@link #VIEWER} le metricas, containers, logs, processos e a lista de servicos.
 * {@link #ADMIN} acrescenta as acoes de escrita (start/stop/restart de container, edicao de
 * servicos) e a gestao de usuarios.
 *
 * <p>A implicacao "ADMIN tambem e' VIEWER" e' resolvida aqui, concedendo as duas autoridades, em vez
 * de um {@code RoleHierarchy}: a hierarquia nao e' aplicada pelo
 * {@code MessageMatcherDelegatingAuthorizationManager} usado na autorizacao do WebSocket, entao um
 * ADMIN sem ROLE_VIEWER explicito passaria nas regras HTTP e falharia nas de mensageria.
 */
public enum DashboardRole {

    VIEWER,
    ADMIN;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    /**
     * Autoridades concedidas por este papel, ja incluindo as que ele implica.
     */
    public List<GrantedAuthority> grantedAuthorities() {
        return switch (this) {
            case VIEWER -> List.of(authority(VIEWER));
            case ADMIN -> List.of(authority(ADMIN), authority(VIEWER));
        };
    }

    /**
     * Uniao das autoridades de varios papeis, sem duplicatas.
     */
    public static Collection<GrantedAuthority> grantedAuthorities(Collection<DashboardRole> roles) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (DashboardRole role : roles) {
            authorities.addAll(role.grantedAuthorities());
        }
        return authorities;
    }

    private static GrantedAuthority authority(DashboardRole role) {
        return new SimpleGrantedAuthority(AUTHORITY_PREFIX + role.name());
    }
}
