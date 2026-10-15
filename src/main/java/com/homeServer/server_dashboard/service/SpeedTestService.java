package com.homeServer.server_dashboard.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Teste de velocidade da conexao com a internet — nao o trafego da interface de rede do servidor,
 * que e' o card "Trafego de Rede", e sim quanto o link aguenta de fato.
 *
 * <p>O teste roda no proprio servidor, contra os endpoints publicos de speed test da Cloudflare
 * ({@code /__down} e {@code /__up}): num homelab o servidor esta na mesma rede de casa, entao o
 * resultado reflete a internet da rede, sem depender da conexao de quem abriu o dashboard.
 *
 * <p>Mede, nesta ordem: latencia (mediana de varios GETs vazios) e jitter, download e upload. As
 * fases de throughput abrem varias conexoes HTTP/1.1 em paralelo (com HTTP/2 tudo seria multiplexado
 * numa conexao TCP so' e links rapidos sairiam subestimados) e descartam o primeiro segundo, em que o
 * TCP ainda esta saindo do slow start.
 *
 * <p>Um teste consome dados de verdade (algumas centenas de MB num link rapido), por isso so' roda
 * sob demanda de um ADMIN ou no intervalo configurado — desligado por padrao.
 */
@Service
public class SpeedTestService {

    private static final Logger log = LoggerFactory.getLogger(SpeedTestService.class);

    private static final int HISTORY_SIZE = 10;
    private static final int LATENCY_SAMPLES = 10;
    private static final long DOWNLOAD_REQUEST_BYTES = 25_000_000L;
    private static final long UPLOAD_REQUEST_BYTES = 25_000_000L;
    private static final Duration WARMUP = Duration.ofSeconds(1);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String USER_AGENT = "server-dashboard-speedtest/1.0";

    public record SpeedTestResult(long startedAt, long finishedAt, double downloadMbps, double uploadMbps,
                                  double latencyMs, double jitterMs, String trigger) {
    }

    public record SpeedTestStatus(boolean running, String phase, Long runningSince, SpeedTestResult lastResult,
                                  String lastError, Long lastErrorAt, List<SpeedTestResult> history,
                                  long intervalMinutes) {
    }

    private final String baseUrl;
    private final Duration phaseDuration;
    private final int streams;
    private final long intervalMinutes;

    private final ExecutorService runner = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "speedtest-runner");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Deque<SpeedTestResult> history = new ArrayDeque<>();

    private volatile String phase;
    private volatile Long runningSince;
    private volatile String lastError;
    private volatile Long lastErrorAt;
    private volatile Instant lastAttemptAt;

    public SpeedTestService(@Value("${dashboard.speedtest.base-url:https://speed.cloudflare.com}") String baseUrl,
                            @Value("${dashboard.speedtest.phase-seconds:8}") long phaseSeconds,
                            @Value("${dashboard.speedtest.streams:4}") int streams,
                            @Value("${dashboard.speedtest.interval-minutes:0}") long intervalMinutes) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.phaseDuration = Duration.ofSeconds(Math.max(2, phaseSeconds));
        this.streams = Math.max(1, Math.min(16, streams));
        this.intervalMinutes = Math.max(0, intervalMinutes);
    }

    /**
     * Dispara um teste em segundo plano. Devolve {@code false} se ja houver um em andamento — dois
     * testes simultaneos disputariam o mesmo link e os dois resultados sairiam errados.
     */
    public boolean startAsync(String trigger) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        runningSince = System.currentTimeMillis();
        lastAttemptAt = Instant.now();
        phase = "latency";
        runner.submit(() -> {
            try {
                runTest(trigger);
            } finally {
                phase = null;
                runningSince = null;
                running.set(false);
            }
        });
        return true;
    }

    /** Execucao periodica, se {@code dashboard.speedtest.interval-minutes} > 0. So' agenda; o teste roda no pool proprio. */
    @Scheduled(initialDelay = 120_000, fixedDelay = 60_000)
    public void runIfDue() {
        if (intervalMinutes <= 0) {
            return;
        }
        Instant last = lastAttemptAt;
        if (last == null || Instant.now().isAfter(last.plus(Duration.ofMinutes(intervalMinutes)))) {
            startAsync("agendado");
        }
    }

    public SpeedTestStatus getStatus() {
        List<SpeedTestResult> snapshot;
        synchronized (history) {
            snapshot = new ArrayList<>(history);
        }
        SpeedTestResult last = snapshot.isEmpty() ? null : snapshot.get(0);
        return new SpeedTestStatus(running.get(), phase, runningSince, last, lastError, lastErrorAt,
                snapshot, intervalMinutes);
    }

    @PreDestroy
    void shutdown() {
        runner.shutdownNow();
    }

    private void runTest(String trigger) {
        long startedAt = System.currentTimeMillis();
        log.info("[SpeedTest] Iniciando teste ({}) contra {}", trigger, baseUrl);
        try (HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(ProxySelector.getDefault())
                .connectTimeout(Duration.ofSeconds(10))
                .build()) {

            phase = "latency";
            double[] latency = measureLatency(client);

            phase = "download";
            double downloadMbps = measureThroughput(downloadWorker(client));

            phase = "upload";
            double uploadMbps = measureThroughput(uploadWorker(client));

            SpeedTestResult result = new SpeedTestResult(startedAt, System.currentTimeMillis(),
                    round(downloadMbps), round(uploadMbps), round(latency[0]), round(latency[1]), trigger);
            synchronized (history) {
                history.addFirst(result);
                while (history.size() > HISTORY_SIZE) {
                    history.removeLast();
                }
            }
            lastError = null;
            lastErrorAt = null;
            log.info("[SpeedTest] Concluido: download={} Mbps, upload={} Mbps, ping={} ms, jitter={} ms",
                    result.downloadMbps(), result.uploadMbps(), result.latencyMs(), result.jitterMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordError("Teste interrompido");
        } catch (Exception e) {
            log.warn("[SpeedTest] Falha no teste de velocidade: {}", e.toString());
            recordError(describe(e));
        }
    }

    private void recordError(String message) {
        lastError = message;
        lastErrorAt = System.currentTimeMillis();
    }

    /** Devolve {mediana, jitter} em ms. A primeira amostra e' descartada: ela paga DNS, TCP e TLS. */
    private double[] measureLatency(HttpClient client) throws IOException, InterruptedException {
        HttpRequest request = request(URI.create(baseUrl + "/__down?bytes=0")).GET().build();
        double[] samples = new double[LATENCY_SAMPLES];
        for (int i = -1; i < LATENCY_SAMPLES; i++) {
            long t0 = System.nanoTime();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            long t1 = System.nanoTime();
            ensureOk(response.statusCode());
            if (i >= 0) {
                samples[i] = (t1 - t0) / 1_000_000.0;
            }
        }
        double jitter = 0;
        for (int i = 1; i < samples.length; i++) {
            jitter += Math.abs(samples[i] - samples[i - 1]);
        }
        jitter /= samples.length - 1;
        double[] sorted = samples.clone();
        Arrays.sort(sorted);
        double median = (sorted[(sorted.length - 1) / 2] + sorted[sorted.length / 2]) / 2;
        return new double[]{median, jitter};
    }

    @FunctionalInterface
    private interface Worker {
        void run(long deadlineNanos, LongAdder bytes) throws Exception;
    }

    /**
     * Roda {@link #streams} workers em paralelo ate o fim da fase e devolve a vazao em Mbps,
     * contando so' os bytes depois do aquecimento.
     */
    private double measureThroughput(Worker worker) throws Exception {
        LongAdder bytes = new LongAdder();
        long start = System.nanoTime();
        long deadline = start + phaseDuration.toNanos();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < streams; i++) {
                futures.add(pool.submit(() -> {
                    worker.run(deadline, bytes);
                    return null;
                }));
            }

            Thread.sleep(WARMUP);
            long warmBytes = bytes.sum();
            long warmTime = System.nanoTime();

            Exception failure = null;
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    if (failure == null && e.getCause() instanceof Exception cause) {
                        failure = cause;
                    }
                }
            }
            long end = Math.min(System.nanoTime(), deadline);
            long measured = bytes.sum() - warmBytes;
            if (measured <= 0 || end <= warmTime) {
                if (failure != null) {
                    throw failure;
                }
                throw new IOException("Nenhum dado transferido");
            }
            if (failure != null) {
                log.debug("[SpeedTest] Um dos fluxos falhou, usando os demais: {}", failure.toString());
            }
            double seconds = (end - warmTime) / 1_000_000_000.0;
            return measured * 8 / seconds / 1_000_000.0;
        }
    }

    private Worker downloadWorker(HttpClient client) {
        return (dl, bytes) -> {
            byte[] buffer = new byte[64 * 1024];
            HttpRequest request = request(URI.create(baseUrl + "/__down?bytes=" + DOWNLOAD_REQUEST_BYTES)).GET().build();
            while (System.nanoTime() < dl) {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream in = response.body()) {
                    ensureOk(response.statusCode());
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        bytes.add(n);
                        if (System.nanoTime() >= dl) {
                            break;
                        }
                    }
                }
            }
        };
    }

    private Worker uploadWorker(HttpClient client) {
        return (dl, bytes) -> {
            byte[] chunk = new byte[64 * 1024];
            ThreadLocalRandom.current().nextBytes(chunk);
            while (System.nanoTime() < dl) {
                HttpRequest request = request(URI.create(baseUrl + "/__up"))
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofInputStream(
                                () -> new UploadStream(chunk, UPLOAD_REQUEST_BYTES, dl, bytes)))
                        .build();
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                ensureOk(response.statusCode());
            }
        };
    }

    /** Corpo de upload que conta o que foi enviado e termina no limite de bytes ou no fim da fase. */
    private static final class UploadStream extends InputStream {
        private final byte[] chunk;
        private final long deadline;
        private final LongAdder counter;
        private long remaining;
        private int offset;

        UploadStream(byte[] chunk, long limit, long deadline, LongAdder counter) {
            this.chunk = chunk;
            this.remaining = limit;
            this.deadline = deadline;
            this.counter = counter;
        }

        @Override
        public int read() {
            byte[] one = new byte[1];
            return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (remaining <= 0 || System.nanoTime() >= deadline) {
                return -1;
            }
            int n = (int) Math.min(Math.min(len, remaining), chunk.length - offset);
            System.arraycopy(chunk, offset, b, off, n);
            offset = (offset + n) % chunk.length;
            remaining -= n;
            counter.add(n);
            return n;
        }
    }

    private static HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).header("User-Agent", USER_AGENT);
    }

    private static void ensureOk(int status) throws IOException {
        if (status < 200 || status >= 300) {
            throw new IOException("Servidor de teste respondeu HTTP " + status);
        }
    }

    private static String describe(Exception e) {
        String msg = e.getMessage();
        if (e instanceof java.net.http.HttpTimeoutException) {
            return "Tempo esgotado ao falar com o servidor de teste";
        }
        if (e instanceof java.net.ConnectException || e instanceof java.nio.channels.UnresolvedAddressException) {
            return "Sem conexao com o servidor de teste";
        }
        return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
