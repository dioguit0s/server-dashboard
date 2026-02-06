package com.homeServer.server_dashboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gerencia a lista de serviços monitorados com persistência em JSON.
 * O arquivo é salvo em data/monitored-services.json (relativo ao working directory).
 */
@Service
public class MonitoredServicesService {

    private static final Path STORAGE_PATH = Paths.get("data", "monitored-services.json");
    private static final List<MonitoredService> DEFAULTS = List.of(
            new MonitoredService("Immich", 2283),
            new MonitoredService("Dashboard", 8080),
            new MonitoredService("Discord Bot", 3000)
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CopyOnWriteArrayList<MonitoredService> services = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        load();
        if (services.isEmpty()) {
            services.addAll(DEFAULTS);
            save();
        }
    }

    public synchronized List<MonitoredService> getAll() {
        return new ArrayList<>(services);
    }

    public synchronized MonitoredService add(String name, int port) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome não pode ser vazio");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Porta inválida (1-65535)");
        }
        for (var s : services) {
            if (s.port() == port) {
                throw new IllegalArgumentException("Já existe um serviço na porta " + port);
            }
        }
        var svc = new MonitoredService(name.trim(), port);
        services.add(svc);
        save();
        return svc;
    }

    public synchronized boolean remove(int port) {
        boolean removed = services.removeIf(s -> s.port() == port);
        if (removed) save();
        return removed;
    }

    private void load() {
        try {
            if (Files.exists(STORAGE_PATH)) {
                var json = Files.readString(STORAGE_PATH);
                var list = objectMapper.readValue(json, new TypeReference<List<MonitoredService>>() {});
                services.clear();
                services.addAll(list != null ? list : List.of());
            }
        } catch (IOException e) {
            // Fallback para defaults em caso de erro
            services.clear();
        }
    }

    private void save() {
        try {
            Files.createDirectories(STORAGE_PATH.getParent());
            var json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(services);
            Files.writeString(STORAGE_PATH, json);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao salvar serviços: " + e.getMessage());
        }
    }

    public record MonitoredService(String name, int port) {}
}
