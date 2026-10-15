package com.homeServer.server_dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homeServer.server_dashboard.service.SpeedTestService.SpeedTestStatus;
import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Roda o teste de verdade contra um servidor HTTP local que imita os endpoints da Cloudflare. */
class SpeedTestServiceTest {

    private HttpServer server;
    private volatile int downStatus = 200;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/__down", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            long size = Long.parseLong(query.substring(query.indexOf('=') + 1));
            if (downStatus != 200) {
                exchange.sendResponseHeaders(downStatus, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, size == 0 ? -1 : size);
            byte[] buf = new byte[64 * 1024];
            try (OutputStream out = exchange.getResponseBody()) {
                for (long left = size; left > 0; left -= buf.length) {
                    out.write(buf, 0, (int) Math.min(buf.length, left));
                }
            } catch (Exception ignored) {
                // cliente cortou o download no fim da fase
            }
        });
        server.createContext("/__up", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                in.transferTo(OutputStream.nullOutputStream());
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private SpeedTestService service() {
        return new SpeedTestService("http://127.0.0.1:" + server.getAddress().getPort(), 2, 2, 0);
    }

    private static SpeedTestStatus awaitFinished(SpeedTestService service) throws InterruptedException {
        Instant limit = Instant.now().plus(Duration.ofSeconds(30));
        SpeedTestStatus status = service.getStatus();
        while (status.running() && Instant.now().isBefore(limit)) {
            Thread.sleep(100);
            status = service.getStatus();
        }
        return status;
    }

    @Test
    void measuresDownloadUploadAndLatency() throws Exception {
        SpeedTestService service = service();
        try {
            assertThat(service.startAsync("manual")).isTrue();
            assertThat(service.startAsync("manual")).as("um teste por vez").isFalse();

            SpeedTestStatus status = awaitFinished(service);

            assertThat(status.running()).isFalse();
            assertThat(status.lastError()).isNull();
            assertThat(status.lastResult()).isNotNull();
            assertThat(status.lastResult().downloadMbps()).isPositive();
            assertThat(status.lastResult().uploadMbps()).isPositive();
            assertThat(status.lastResult().latencyMs()).isGreaterThanOrEqualTo(0);
            assertThat(status.lastResult().trigger()).isEqualTo("manual");
            assertThat(status.history()).hasSize(1);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void reportsFailureWithoutResult() throws Exception {
        downStatus = 503;
        SpeedTestService service = service();
        try {
            assertThat(service.startAsync("manual")).isTrue();
            SpeedTestStatus status = awaitFinished(service);

            assertThat(status.running()).isFalse();
            assertThat(status.lastResult()).isNull();
            assertThat(status.lastError()).contains("503");
            assertThat(service.startAsync("manual")).as("libera para um novo teste").isTrue();
        } finally {
            service.shutdown();
        }
    }
}
