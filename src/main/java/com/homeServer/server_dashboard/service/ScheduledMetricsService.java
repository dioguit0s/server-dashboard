package com.homeServer.server_dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScheduledMetricsService {

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final List<ServiceCheck> servicesToCheck = List.of(
            new ServiceCheck("Immich", 2283),
            new ServiceCheck("Dashboard", 8080),
            new ServiceCheck("Discord Bot", 3000)
    );

    @Scheduled(fixedRate = 1000) // Atualiza a cada 1 segundo
    public void sendMetrics() {
        double cpu = monitorService.getCpuUsage();
        double ram = monitorService.getMemoryUsagePercentage();
        MonitorService.DiskInfo disk = monitorService.getDiskMetrics();
        double temp = monitorService.getCpuTemperature();
        String uptime = monitorService.getSystemUptime();
        MonitorService.NetworkInfo net = monitorService.getNetworkMetrics();

        List<Map<String, Object>> servicesStatus = new ArrayList<>();
        for (ServiceCheck service : servicesToCheck) {
            boolean isUp = monitorService.isServiceUp(service.port);
            Map<String, Object> status = new HashMap<>();
            status.put("name", service.name);
            status.put("online", isUp);
            status.put("port", service.port);
            servicesStatus.add(status);
        }

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("cpuPercent", String.format("%.1f", cpu));
        metrics.put("cpuInt", (int) cpu);
        metrics.put("ramPercent", String.format("%.1f", ram));
        metrics.put("ramInt", (int) ram);
        metrics.put("ramLivre", monitorService.formatMemory(monitorService.getFreeMemory()));
        metrics.put("diskPercent", String.format("%.1f", disk.percent));
        metrics.put("diskInt", (int) disk.percent);
        metrics.put("diskFree", disk.free);
        metrics.put("cpuTemp", String.format("%.1f", temp));
        metrics.put("cpuTempInt", (int) temp);
        metrics.put("uptime", uptime);
        metrics.put("netDown", net.downloadRate);
        metrics.put("netUp", net.uploadRate);
        metrics.put("services", servicesStatus);

        messagingTemplate.convertAndSend("/topic/metrics", (Object) metrics);
    }

    record ServiceCheck(String name, int port) {}
}