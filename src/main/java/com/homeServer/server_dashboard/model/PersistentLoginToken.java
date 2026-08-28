package com.homeServer.server_dashboard.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Token do "lembrar de mim", no esquema de series persistentes do Spring Security
 * ({@code PersistentTokenRepository}): cada dispositivo lembrado tem uma serie fixa, cujo
 * {@code tokenValue} e' trocado a cada uso (impede replay de um cookie roubado apos o uso legitimo
 * seguinte).
 */
@Entity
@Table(name = "persistent_login_token")
public class PersistentLoginToken {

    @Id
    @Column(length = 64)
    private String series;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 64)
    private String tokenValue;

    @Column(nullable = false)
    private Instant lastUsed;

    protected PersistentLoginToken() {
    }

    public PersistentLoginToken(String series, String username, String tokenValue, Instant lastUsed) {
        this.series = series;
        this.username = username;
        this.tokenValue = tokenValue;
        this.lastUsed = lastUsed;
    }

    public String getSeries() {
        return series;
    }

    public String getUsername() {
        return username;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public void setTokenValue(String tokenValue) {
        this.tokenValue = tokenValue;
    }

    public Instant getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(Instant lastUsed) {
        this.lastUsed = lastUsed;
    }
}
