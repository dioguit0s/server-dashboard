package com.homeServer.server_dashboard.service;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class DockerService {

    private static final Logger log = LoggerFactory.getLogger(DockerService.class);
    private static final int COMMAND_TIMEOUT_SECONDS = 30;

    /**
     * Referência de container Docker: ID (hex), nome ou hash parcial — caracteres seguros para argv (sem shell).
     */
    private static final Pattern CONTAINER_REF_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}$");

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class DockerContainerInformation {
        public String containerIdentifier;
        public String containerName;
        public String containerState;
        public String containerStatus;
        public String cpuPercentage;
        public String memoryPercentage;

        public DockerContainerInformation(String containerIdentifier, String containerName, String containerState,
                                        String containerStatus, String cpuPercentage, String memoryPercentage) {
            this.containerIdentifier = containerIdentifier;
            this.containerName = containerName;
            this.containerState = containerState;
            this.containerStatus = containerStatus;
            this.cpuPercentage = cpuPercentage;
            this.memoryPercentage = memoryPercentage;
        }
    }

    public List<DockerContainerInformation> retrieveAllContainers() {
        Map<String, DockerContainerInformation> containersById = new LinkedHashMap<>();

        CommandResult psResult = runDockerCommand(new ProcessBuilder(
                "docker", "ps", "-a",
                "--format", "{{.ID}}\t{{.Names}}\t{{.State}}\t{{.Status}}"));
        if (!psResult.success()) {
            log.warn("docker ps falhou: {}", psResult.output());
            return List.of();
        }

        for (String line : psResult.output().split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\t", 4);
            if (parts.length < 4) {
                continue;
            }
            String id = parts[0].trim();
            containersById.put(id, new DockerContainerInformation(
                    id,
                    parts[1].trim(),
                    parts[2].trim(),
                    parts[3].trim(),
                    "0.00%",
                    "0.00%"
            ));
        }

        CommandResult statsResult = runDockerCommand(new ProcessBuilder(
                "docker", "stats", "--no-stream",
                "--format", "{{.ID}}\t{{.CPUPerc}}\t{{.MemPerc}}"));
        if (statsResult.success()) {
            for (String line : statsResult.output().split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split("\t", 3);
                if (parts.length < 3) {
                    continue;
                }
                DockerContainerInformation container = containersById.get(parts[0].trim());
                if (container != null) {
                    container.cpuPercentage = parts[1].trim();
                    container.memoryPercentage = parts[2].trim();
                }
            }
        } else if (!statsResult.output().isBlank()) {
            log.debug("docker stats sem métricas (containers parados ou indisponível): {}", statsResult.output());
        }

        return new ArrayList<>(containersById.values());
    }

    public boolean executeActionOnContainer(String actionName, String containerIdentifier) {
        if (containerIdentifier == null || !CONTAINER_REF_PATTERN.matcher(containerIdentifier.trim()).matches()) {
            log.warn("Identificador de container rejeitado por validação: {}", containerIdentifier);
            return false;
        }
        String id = containerIdentifier.trim();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("docker", actionName, id);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            try (BufferedReader errIn = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                while (errIn.readLine() != null) {
                    // consome saída unificada (stdout+stderr) para evitar bloqueio por buffer cheio
                }
            }
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("docker {} {} excedeu o tempo limite (120s)", actionName, id);
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception exception) {
            log.warn("Falha ao executar docker {} {}: {}", actionName, id, exception.getMessage());
            return false;
        }
    }

    private record CommandResult(boolean success, String output) {}

    private CommandResult runDockerCommand(ProcessBuilder processBuilder) {
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(line);
                }
                output = builder.toString();
            }
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new CommandResult(false, "Comando docker excedeu o tempo limite (" + COMMAND_TIMEOUT_SECONDS + "s)");
            }
            if (process.exitValue() != 0) {
                return new CommandResult(false, output.isBlank()
                        ? "Comando docker falhou com código " + process.exitValue()
                        : output);
            }
            return new CommandResult(true, output);
        } catch (Exception exception) {
            return new CommandResult(false, exception.getMessage());
        }
    }
}
