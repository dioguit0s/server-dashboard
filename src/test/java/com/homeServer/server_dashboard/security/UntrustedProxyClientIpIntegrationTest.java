package com.homeServer.server_dashboard.security;

import static com.homeServer.server_dashboard.security.ClientIpResolutionIntegrationTest.location;
import static com.homeServer.server_dashboard.security.ClientIpResolutionIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * O CF-Connecting-IP so' e' confiavel quando a conexao vem do cloudflared. Vindo de qualquer outro
 * endereco (ex.: um cliente da LAN acessando a porta direto), o header e' ignorado e conta o IP real
 * da conexao — senao bastaria manda-lo para trocar de identidade a cada tentativa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "dashboard.security.login.max-attempts=3",
        "dashboard.security.login.global-max-attempts=0",
        // Nenhum proxy confiavel que case com o loopback do teste.
        "dashboard.client-ip.trusted-proxies=10\\.255\\.255\\.255"
})
class UntrustedProxyClientIpIntegrationTest {

    private static final int MAX_ATTEMPTS = 3;

    @LocalServerPort
    private int port;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Test
    void cloudflareHeaderFromAnUntrustedPeerIsIgnored() throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            login(port, client, Map.of("CF-Connecting-IP", "203.0.113." + attempt));
        }

        String blocked = location(login(port, client, Map.of("CF-Connecting-IP", "203.0.113.99")));

        assertThat(blocked).endsWith("/login?blocked");
        assertThat(loginAttemptService.isBlocked("127.0.0.1")).isTrue();
        assertThat(loginAttemptService.isBlocked("203.0.113.0")).isFalse();
    }
}
