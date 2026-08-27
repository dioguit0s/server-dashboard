package com.homeServer.server_dashboard.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.homeServer.server_dashboard.model.DashboardRole;
import com.homeServer.server_dashboard.model.DashboardUser;

/**
 * {@link UserDetails} do dashboard. Alem do que o Spring Security exige, carrega o id e o estado do
 * 2FA para que o fluxo de login em dois passos nao precise voltar ao banco a cada decisao.
 */
public class DashboardUserDetails implements UserDetails {

    private final Long identifier;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final boolean totpEnabled;
    private final Collection<GrantedAuthority> authorities;

    public DashboardUserDetails(DashboardUser user) {
        this.identifier = user.getIdentifier();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.enabled = user.isEnabled();
        this.totpEnabled = user.isTotpEnabled();
        this.authorities = DashboardRole.grantedAuthorities(user.getRoles());
    }

    public Long getIdentifier() {
        return identifier;
    }

    public boolean isTotpEnabled() {
        return totpEnabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
