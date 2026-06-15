package com.homeServer.server_dashboard.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DockerService {

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
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("docker", "stats", "--all", "--no-stream", "--format", "{{.ID}}|{{.Names}}|{{.State}}|{{.Status}}|{{.CPUPerc}}|{{.MemPerc}}");
            Process process = processBuilder.start();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String outputLine;

            while ((outputLine = bufferedReader.readLine()) != null) {
                String[] lineParts = outputLine.split("\\|");
                if (lineParts.length >= 6) {
                    containerList.add(new DockerContainerInformation(
                            lineParts[0], lineParts[1], lineParts[2], lineParts[3], lineParts[4], lineParts[5]
                    ));
                }
            }
        } catch (Exception exception) {
            System.out.println("Erro ao ler dados do Docker: " + exception.getMessage());
        }
        return containerList;
    }

    public boolean executeActionOnContainer(String actionName, String containerIdentifier) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("docker", actionName, containerIdentifier);
            Process process = processBuilder.start();
            int exitCodeValue = process.waitFor();
            return exitCodeValue == 0;
        } catch (Exception exception) {
            return false;
        }
    }

    public String retrieveContainerLogs(String containerIdentifier, int tailLines) {
        int boundedTailLines = Math.max(50, Math.min(500, tailLines));
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "docker", "logs", "--tail", String.valueOf(boundedTailLines), containerIdentifier);
            Process process = processBuilder.start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            int exitCodeValue = process.waitFor();
            if (exitCodeValue != 0) {
                return null;
            }
            return output;
        } catch (Exception exception) {
            return null;
        }
    }
}