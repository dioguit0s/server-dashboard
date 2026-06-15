package com.homeServer.server_dashboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class DockerService {

    private static final Logger log = LoggerFactory.getLogger(DockerService.class);

    /**
     * Referência de container Docker: ID (hex), nome ou hash parcial — caracteres seguros para argv (sem shell).
     */
    private static final Pattern CONTAINER_REF_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}$");

    public static class DockerContainerInformation {
        public String containerIdentifier;
        public String containerName;
        public String containerState;
        public String containerStatus;
        public String cpuPercentage;
        public String memoryPercentage;

        public DockerContainerInformation(String containerIdentifier, String containerName, String containerState, String containerStatus, String cpuPercentage, String memoryPercentage) {
            this.containerIdentifier = containerIdentifier;
            this.containerName = containerName;
            this.containerState = containerState;
            this.containerStatus = containerStatus;
            this.cpuPercentage = cpuPercentage;
            this.memoryPercentage = memoryPercentage;
        }
    }

    public List<DockerContainerInformation> retrieveAllContainers() {
        List<DockerContainerInformation> containerList = new ArrayList<>();
        ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "stats", "--all", "--no-stream", "--format",
                "{{.ID}}|{{.Names}}|{{.State}}|{{.Status}}|{{.CPUPerc}}|{{.MemPerc}}");
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            try (BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String outputLine;
                while ((outputLine = bufferedReader.readLine()) != null) {
                    String[] lineParts = outputLine.split("\\|");
                    if (lineParts.length >= 6) {
                        containerList.add(new DockerContainerInformation(
                                lineParts[0], lineParts[1], lineParts[2], lineParts[3], lineParts[4], lineParts[5]
                        ));
                    }
                }
            }
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("docker stats excedeu o tempo limite (60s)");
            }
        } catch (Exception exception) {
            log.warn("Erro ao ler dados do Docker: {}", exception.getMessage());
        }
        return containerList;
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

}
