package com.homeServer.server_dashboard.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * O rate limiting do login so' vale se o IP for o do visitante, e nao um valor escolhido por ele.
 * Atras do Cloudflare Tunnel, o X-Forwarded-For chega com o valor do cliente na frente (a Cloudflare
 * acrescenta, nao sobrescreve); o IP confiavel e' o CF-Connecting-IP. Estes testes passam pelo
 * Tomcat de verdade (porta aleatoria, conexao pelo loopback — o mesmo caminho do cloudflared), ja
 * que o MockMvc nao executa a valvula que resolve o IP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "dashboard.security.login.max-attempts=3",
        "dashboard.security.login.global-max-attempts=0"
})
class ClientIpResolutionIntegrationTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final String VISITOR_IP = "203.0.113.7";
    private static final String OTHER_VISITOR_IP = "198.51.100.20";

    @LocalServerPort
    private int port;

    @Autowired
    private LoginAttemptService loginAttemptService;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @BeforeEach
    void resetCounters() {
        for (String address : new String[]{VISITOR_IP, OTHER_VISITOR_IP, "127.0.0.1", "0:0:0:0:0:0:0:1"}) {
            loginAttemptService.recordSuccess(address);
        }
    }

    @Test
    void forgedForwardedForDoesNotEscapeTheLockout() throws Exception {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpResponse<Void> response = failedLogin(Map.of(
                    "CF-Connecting-IP", VISITOR_IP,
                    "X-Forwarded-For", "10.0.0." + attempt + ", " + VISITOR_IP));
            assertThat(location(response)).endsWith("/login?error");
        }

        HttpResponse<Void> blocked = failedLogin(Map.of(
                "CF-Connecting-IP", VISITOR_IP,
                "X-Forwarded-For", "10.0.0.99, " + VISITOR_IP));

        assertThat(location(blocked)).endsWith("/login?blocked");
        assertThat(loginAttemptService.isBlocked(VISITOR_IP)).isTrue();
    }

    @Test
    void eachVisitorIsCountedSeparately() throws Exception {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            failedLogin(Map.of("CF-Connecting-IP", VISITOR_IP));
        }

        HttpResponse<Void> other = failedLogin(Map.of("CF-Connecting-IP", OTHER_VISITOR_IP));

        assertThat(location(other)).endsWith("/login?error");
        assertThat(loginAttemptService.isBlocked(VISITOR_IP)).isTrue();
        assertThat(loginAttemptService.isBlocked(OTHER_VISITOR_IP)).isFalse();
    }

    @Test
    void withoutTheCloudflareHeaderTheConnectionAddressIsUsed() throws Exception {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            failedLogin(Map.of("X-Forwarded-For", "10.0.0." + attempt));
        }

        HttpResponse<Void> blocked = failedLogin(Map.of("X-Forwarded-For", "10.0.0.99"));

        assertThat(location(blocked)).endsWith("/login?blocked");
    }

    @Test
    void forwardedProtoFromTheTunnelIsHonored() throws Exception {
        HttpResponse<Void> response = failedLogin(Map.of(
                "CF-Connecting-IP", VISITOR_IP,
                "X-Forwarded-Proto", "https"));

        assertThat(location(response)).startsWith("https://");
    }

    private HttpResponse<Void> failedLogin(Map<String, String> headers) throws IOException, InterruptedException {
        return login(port, client, headers);
    }

    static HttpResponse<Void> login(int port, HttpClient client, Map<String, String> headers)
            throws IOException, InterruptedException {
        // CsrfTokenRequestAttributeHandler (sem XOR): o valor do cookie volta cru no header.
        String csrfToken = "test-csrf-token";
        Map<String, String> allHeaders = new LinkedHashMap<>(headers);
        allHeaders.put("Cookie", "XSRF-TOKEN=" + csrfToken);
        allHeaders.put("X-XSRF-TOKEN", csrfToken);
        allHeaders.put("Content-Type", "application/x-www-form-urlencoded");

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/login"))
                .POST(HttpRequest.BodyPublishers.ofString("username=admin&password=senha-errada"));
        allHeaders.forEach(request::header);
        return client.send(request.build(), HttpResponse.BodyHandlers.discarding());
    }

    static String location(HttpResponse<?> response) {
        assertThat(response.statusCode()).isEqualTo(302);
        return response.headers().firstValue("Location").orElseThrow();
    }
}
