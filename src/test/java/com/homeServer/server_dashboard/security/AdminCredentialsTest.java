package com.homeServer.server_dashboard.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminCredentialsTest {

    private static final String USERNAME = "admin";
    private static final String PLAIN_PASSWORD = "senha-secreta";

    private final PasswordEncoder passwordEncoder = passwordEncoder();

    @Test
    void hashesThePlainPasswordWhenOnlyItIsConfigured() {
        AdminCredentials credentials = credentials(PLAIN_PASSWORD, null);

        assertThat(credentials.username()).isEqualTo(USERNAME);
        assertThat(credentials.encodedPassword())
                .startsWith("{bcrypt}$2")
                .doesNotContain(PLAIN_PASSWORD);
        assertThat(passwordEncoder.matches(PLAIN_PASSWORD, credentials.encodedPassword())).isTrue();
    }

    @Test
    void keepsAPrefixedHashAsItIs() {
        String hash = passwordEncoder.encode(PLAIN_PASSWORD);

        assertThat(credentials(null, hash).encodedPassword()).isEqualTo(hash);
    }

    @Test
    void addsTheBcryptPrefixToAHashGeneratedByAnExternalTool() {
        String bareHash = new BCryptPasswordEncoder().encode(PLAIN_PASSWORD);

        AdminCredentials credentials = credentials(null, bareHash);

        assertThat(credentials.encodedPassword()).isEqualTo("{bcrypt}" + bareHash);
        assertThat(passwordEncoder.matches(PLAIN_PASSWORD, credentials.encodedPassword())).isTrue();
    }

    @Test
    void prefersTheHashOverThePlainPassword() {
        String hash = passwordEncoder.encode("senha-do-hash");

        AdminCredentials credentials = credentials(PLAIN_PASSWORD, hash);

        assertThat(credentials.encodedPassword()).isEqualTo(hash);
        assertThat(passwordEncoder.matches(PLAIN_PASSWORD, credentials.encodedPassword())).isFalse();
    }

    @Test
    void failsFastWhenNoCredentialIsConfigured() {
        assertThatThrownBy(() -> credentials("   ", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DASHBOARD_ADMIN_PASSWORD_HASH");
    }

    @Test
    void failsFastWhenTheUsernameIsMissing() {
        assertThatThrownBy(() -> new AdminCredentials(" ", PLAIN_PASSWORD, null, passwordEncoder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DASHBOARD_ADMIN_USERNAME");
    }

    @Test
    void rejectsAHashThatIsNotAHash() {
        assertThatThrownBy(() -> credentials(null, "senha-em-texto-puro"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BCrypt");
    }

    @Test
    void rejectsANoopHashThatWouldKeepThePasswordInPlainText() {
        assertThatThrownBy(() -> credentials(null, "{noop}" + PLAIN_PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{noop}");
    }

    private AdminCredentials credentials(String plainPassword, String passwordHash) {
        return new AdminCredentials(USERNAME, plainPassword, passwordHash, passwordEncoder);
    }

    /**
     * Mesma configuracao do bean de producao: delegating com BCrypt como padrao para {@code matches()}.
     */
    private static PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder encoder =
                (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        encoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return encoder;
    }
}
