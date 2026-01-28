package com.homeServer.server_dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class ScheduledMetricsService {

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedRate = 1000) // Atualiza a cada 1 segundo
    public void sendMetrics() {
        double cpu = monitorService.getCpuUsage();
        double ram = monitorService.getMemoryUsagePercentage();
        MonitorService.DiskInfo disk = monitorService.getDiskMetrics();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("cpuPercent", String.format("%.1f", cpu));
        metrics.put("cpuInt", (int) cpu);
        metrics.put("ramPercent", String.format("%.1f", ram));
        metrics.put("ramInt", (int) ram);
        metrics.put("ramLivre", monitorService.formatMemory(monitorService.getFreeMemory()));
        metrics.put("diskPercent", String.format("%.1f", disk.percent));
        metrics.put("diskInt", (int) disk.percent);
        metrics.put("diskFree", disk.free);

        messagingTemplate.convertAndSend("/topic/metrics", (Object) metrics);
    }
}