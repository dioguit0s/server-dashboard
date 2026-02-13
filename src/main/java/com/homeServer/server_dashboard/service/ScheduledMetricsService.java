package com.homeServer.server_dashboard.service;

import com.homeServer.server_dashboard.service.MonitoredServicesService.MonitoredService;
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

    private static final int TOP_PROCESSES_LIMIT = 10;

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private MonitoredServicesService monitoredServicesService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Retorna as métricas públicas (CPU, RAM, disco, temp, uptime, rede)
     * para uso em API REST e fallback de polling quando WebSocket falha.
     */
    public Map<String, Object> getPublicMetrics() {
        double cpu = monitorService.getCpuUsage();
        double ram = monitorService.getMemoryUsagePercentage();
        MonitorService.DiskInfo disk = monitorService.getDiskMetrics();
        double temp = monitorService.getCpuTemperature();
        String uptime = monitorService.getSystemUptime();
        MonitorService.NetworkInfo net = monitorService.getNetworkMetrics();

        Map<String, Object> publicMetrics = new HashMap<>();
        publicMetrics.put("cpuPercent", String.format("%.1f", cpu));
        publicMetrics.put("cpuInt", (int) cpu);
        publicMetrics.put("ramPercent", String.format("%.1f", ram));
        publicMetrics.put("ramInt", (int) ram);
        publicMetrics.put("ramLivre", monitorService.formatMemory(monitorService.getFreeMemory()));
        publicMetrics.put("diskPercent", String.format("%.1f", disk.percent));
        publicMetrics.put("diskInt", (int) disk.percent);
        publicMetrics.put("diskFree", disk.free);
        publicMetrics.put("cpuTemp", String.format("%.1f", temp));
        publicMetrics.put("cpuTempInt", (int) temp);
        publicMetrics.put("uptime", uptime);
        publicMetrics.put("netDown", net.downloadRate);
        publicMetrics.put("netUp", net.uploadRate);

        return publicMetrics;
    }

    @Scheduled(fixedRate = 1000)
    public void sendMetrics() {
        Map<String, Object> publicMetrics = getPublicMetrics();

        List<Map<String, Object>> servicesStatus = new ArrayList<>();
        for (MonitoredService svc : monitoredServicesService.getAll()) {
            boolean isUp = monitorService.isServiceUp(svc.port());
            Map<String, Object> status = new HashMap<>();
            status.put("name", svc.name());
            status.put("online", isUp);
            status.put("port", svc.port());
            servicesStatus.add(status);
        }

        List<Map<String, Object>> processesByCpu = toProcessMaps(monitorService.getTopProcesses("cpu", TOP_PROCESSES_LIMIT));
        List<Map<String, Object>> processesByRam = toProcessMaps(monitorService.getTopProcesses("ram", TOP_PROCESSES_LIMIT));

        Map<String, Object> adminMetrics = new HashMap<>();
        adminMetrics.put("services", servicesStatus);
        adminMetrics.put("processesByCpu", processesByCpu);
        adminMetrics.put("processesByRam", processesByRam);

        messagingTemplate.convertAndSend("/topic/public", (Object) publicMetrics);
        messagingTemplate.convertAndSend("/topic/admin", (Object) adminMetrics);
    }

    private List<Map<String, Object>> toProcessMaps(List<MonitorService.ProcessInfo> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var p : list) {
            Map<String, Object> pm = new HashMap<>();
            pm.put("name", p.name);
            pm.put("pid", p.pid);
            pm.put("cpuPercent", p.cpuPercent);
            pm.put("ramPercent", p.ramPercent);
            pm.put("ramFormatted", p.ramFormatted);
            result.add(pm);
        }
        return result;
    }
}