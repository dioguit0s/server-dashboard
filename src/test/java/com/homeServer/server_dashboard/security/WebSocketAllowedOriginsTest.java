package com.homeServer.server_dashboard.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebSocketAllowedOriginsTest {

    private static final List<String> NO_PROFILE = List.of();
    private static final List<String> DEV_PROFILE = List.of("dev");

    @ParameterizedTest
    @ValueSource(strings = {"*", "*:*", "//*", "http://*", "https://*", "*://*", "https://*:*", " HTTP://*  "})
    void treatsAPatternThatMatchesAnyHostAsWildcard(String pattern) {
        assertThat(WebSocketAllowedOrigins.isWildcard(pattern)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://meudominio.com", "http://localhost:8080", "https://*.meudominio.com",
            "http://localhost:*", "https://192.168.0.10:8080"})
    void doesNotTreatARealRestrictionAsWildcard(String pattern) {
        assertThat(WebSocketAllowedOrigins.isWildcard(pattern)).isFalse();
    }

    @Test
    void failsFastWhenTheWildcardIsConfiguredOutsideDevelopment() {
        assertThatThrownBy(() -> origins("*", NO_PROFILE, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DASHBOARD_WS_ORIGINS");
    }

    @Test
    void failsFastWhenTheWildcardIsHiddenAmongValidOrigins() {
        assertThatThrownBy(() -> origins("https://meudominio.com, http://*", NO_PROFILE, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("http://*");
    }

    @Test
    void failsFastWhenNothingIsConfigured() {
        assertThatThrownBy(() -> origins("   ", NO_PROFILE, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DASHBOARD_WS_ORIGINS");
    }

    @Test
    void failsFastWhenTheActiveProfileIsNotADevelopmentOne() {
        assertThatThrownBy(() -> origins("*", List.of("prod"), false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsTheWildcardUnderADevelopmentProfile() {
        WebSocketAllowedOrigins allowedOrigins = origins("*", DEV_PROFILE, false);

        assertThat(allowedOrigins.patterns()).containsExactly("*");
        assertThat(allowedOrigins.hasWildcard()).isTrue();
    }

    @Test
    void acceptsTheWildcardWhenItIsExplicitlyAllowed() {
        assertThatCode(() -> origins("*", NO_PROFILE, true)).doesNotThrowAnyException();
    }

    @Test
    void keepsTheConfiguredOriginsWithoutAWildcard() {
        WebSocketAllowedOrigins allowedOrigins =
                origins(" https://meudominio.com , http://localhost:8080 ,, ", NO_PROFILE, false);

        assertThat(allowedOrigins.patterns()).containsExactly("https://meudominio.com", "http://localhost:8080");
        assertThat(allowedOrigins.hasWildcard()).isFalse();
    }

    @Test
    void acceptsASubdomainPatternOutsideDevelopment() {
        assertThat(origins("https://*.meudominio.com", NO_PROFILE, false).patterns())
                .containsExactly("https://*.meudominio.com");
    }

    private static WebSocketAllowedOrigins origins(String rawPatterns, List<String> activeProfiles,
                                                   boolean wildcardExplicitlyAllowed) {
        return new WebSocketAllowedOrigins(rawPatterns, activeProfiles, wildcardExplicitlyAllowed);
    }
}
