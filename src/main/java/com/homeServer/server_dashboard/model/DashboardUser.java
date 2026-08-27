package com.homeServer.server_dashboard.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * Usuario do dashboard. Substitui o admin unico em memoria: varias contas, cada uma com seus
 * papeis, sua senha em hash e seu proprio 2FA.
 */
@Entity
@Table(name = "dashboard_user")
public class DashboardUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long identifier;

    /**
     * Guardado sempre em minusculas (ver {@code DashboardUserService.normalizeUsername}), para que
     * a unicidade do banco case com a busca {@code IgnoreCase} do login.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dashboard_user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Set<DashboardRole> roles = EnumSet.noneOf(DashboardRole.class);

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Segredo TOTP em Base32. Fica preenchido durante o enrollment, antes mesmo de
     * {@link #totpEnabled} virar true — a confirmacao com um codigo valido e' que liga o 2FA.
     */
    @Column(length = 64)
    private String totpSecret;

    @Column(nullable = false)
    private boolean totpEnabled;

    /**
     * Ultimo passo de tempo TOTP aceito, para que o mesmo codigo nao seja reaproveitado dentro da
     * janela de validade (anti-replay).
     */
    private Long lastUsedTimeStep;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dashboard_user_recovery_code", joinColumns = @JoinColumn(name = "user_id"))
    private List<RecoveryCode> recoveryCodes = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public DashboardUser() {
    }

    public DashboardUser(String username, String passwordHash, Set<DashboardRole> roles) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = EnumSet.copyOf(roles);
    }

    public Long getIdentifier() {
        return identifier;
    }

    public void setIdentifier(Long identifier) {
        this.identifier = identifier;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Set<DashboardRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<DashboardRole> roles) {
        this.roles = roles.isEmpty() ? EnumSet.noneOf(DashboardRole.class) : EnumSet.copyOf(roles);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    public boolean isTotpEnabled() {
        return totpEnabled;
    }

    public void setTotpEnabled(boolean totpEnabled) {
        this.totpEnabled = totpEnabled;
    }

    public Long getLastUsedTimeStep() {
        return lastUsedTimeStep;
    }

    public void setLastUsedTimeStep(Long lastUsedTimeStep) {
        this.lastUsedTimeStep = lastUsedTimeStep;
    }

    public List<RecoveryCode> getRecoveryCodes() {
        return recoveryCodes;
    }

    public void setRecoveryCodes(List<RecoveryCode> recoveryCodes) {
        this.recoveryCodes = recoveryCodes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean hasRole(DashboardRole role) {
        return roles.contains(role);
    }

    /**
     * Desliga o 2FA e apaga tudo que dependia dele — segredo, anti-replay e codigos de recuperacao.
     */
    public void clearTwoFactor() {
        this.totpEnabled = false;
        this.totpSecret = null;
        this.lastUsedTimeStep = null;
        this.recoveryCodes.clear();
    }

    public long unusedRecoveryCodeCount() {
        return recoveryCodes.stream().filter(code -> !code.isUsed()).count();
    }
}
