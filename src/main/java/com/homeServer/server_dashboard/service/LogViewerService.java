package com.homeServer.server_dashboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LogViewerService {

    private static final Logger log = LoggerFactory.getLogger(LogViewerService.class);

    private static final int COMMAND_TIMEOUT_SECONDS = 10;
    private static final int MIN_TAIL = 10;
    private static final int MAX_TAIL = 500;

    private static final Pattern CONTAINER_REF_PATTERN =
            Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}$");
    private static final Pattern UNIT_PATTERN = Pattern.compile("^[a-zA-Z0-9@._-]+$");

    private final DockerService dockerService;

    public LogViewerService(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    public record LogFetchResult(boolean success, String logs, String error, int exitCode) {
        public static LogFetchResult ok(String logs) {
            return new LogFetchResult(true, logs, null, 0);
        }

        public static LogFetchResult fail(String error, int exitCode) {
            return new LogFetchResult(false, null, error, exitCode);
        }
    }

    public int clampTail(int tail) {
        return Math.max(MIN_TAIL, Math.min(MAX_TAIL, tail));
    }

    public LogFetchResult fetchDockerLogs(String container, int tail) {
        if (container == null || !CONTAINER_REF_PATTERN.matcher(container.trim()).matches()) {
            return LogFetchResult.fail("Identificador de container inválido", -1);
        }
        String id = container.trim();
        int boundedTail = clampTail(tail);
        return runCommand(new ProcessBuilder(
                "docker", "logs", "--tail", String.valueOf(boundedTail), id));
    }

    public LogFetchResult fetchJournalLogs(String unit, int tail) {
        if (unit == null || !UNIT_PATTERN.matcher(unit.trim()).matches()) {
            return LogFetchResult.fail("Nome de unit systemd inválido", -1);
        }
        String sanitizedUnit = unit.trim();
        int boundedTail = clampTail(tail);
        return runCommand(new ProcessBuilder(
                "journalctl", "-u", sanitizedUnit,
                "-n", String.valueOf(boundedTail),
                "--no-pager", "-o", "short-iso"));
    }

    public List<String> listRunningSystemdUnits() {
        CommandResult result = runCommandRaw(new ProcessBuilder(
                "systemctl", "list-units",
                "--type=service",
                "--state=running",
                "--no-pager", "--plain", "--no-legend"));
        if (!result.timedOut() && result.exitCode() == 0) {
            List<String> units = new ArrayList<>();
            for (String line : result.output().split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String unitName = trimmed.split("\\s+")[0];
                if (UNIT_PATTERN.matcher(unitName).matches()) {
                    units.add(unitName);
                }
            }
            Collections.sort(units);
            return units;
        }
        if (result.timedOut()) {
            log.warn("systemctl list-units excedeu o tempo limite ({}s)", COMMAND_TIMEOUT_SECONDS);
        } else if (result.exitCode() != 0) {
            log.warn("systemctl list-units falhou (exit {}): {}", result.exitCode(), result.output());
        }
        return List.of();
    }

    public Map<String, Object> listSources() {
        List<Map<String, String>> containers = dockerService.retrieveAllContainers().stream()
                .map(c -> Map.of(
                        "id", nullToEmpty(c.containerIdentifier),
                        "name", nullToEmpty(c.containerName),
                        "state", nullToEmpty(c.containerState),
                        "status", nullToEmpty(c.containerStatus)))
                .collect(Collectors.toList());

        List<String> units = listRunningSystemdUnits();
        boolean unitsAvailable = !units.isEmpty();

        Map<String, Object> sources = new HashMap<>();
        sources.put("containers", containers);
        sources.put("units", units);
        sources.put("unitsAvailable", unitsAvailable);
        return sources;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private LogFetchResult runCommand(ProcessBuilder processBuilder) {
        CommandResult result = runCommandRaw(processBuilder);
        if (result.timedOut()) {
            return LogFetchResult.fail("Comando excedeu o tempo limite (" + COMMAND_TIMEOUT_SECONDS + "s)", -1);
        }
        if (result.exitCode() != 0) {
            String message = result.output().isBlank()
                    ? "Comando falhou com código " + result.exitCode()
                    : result.output().trim();
            return LogFetchResult.fail(message, result.exitCode());
        }
        return LogFetchResult.ok(result.output());
    }

    private record CommandResult(String output, int exitCode, boolean timedOut) {}

    private CommandResult runCommandRaw(ProcessBuilder processBuilder) {
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new CommandResult(output, -1, true);
            }
            return new CommandResult(output, process.exitValue(), false);
        } catch (Exception exception) {
            log.warn("Falha ao executar comando {}: {}", processBuilder.command(), exception.getMessage());
            return new CommandResult(exception.getMessage(), -1, false);
        }
    }
}
