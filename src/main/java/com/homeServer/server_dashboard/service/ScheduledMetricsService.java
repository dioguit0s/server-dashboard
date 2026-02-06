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

    @Scheduled(fixedRate = 1000)
    public void sendMetrics() {
        double cpu = monitorService.getCpuUsage();
        double ram = monitorService.getMemoryUsagePercentage();
        MonitorService.DiskInfo disk = monitorService.getDiskMetrics();
        double temp = monitorService.getCpuTemperature();
        String uptime = monitorService.getSystemUptime();
        MonitorService.NetworkInfo net = monitorService.getNetworkMetrics();

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
        metrics.put("processesByCpu", processesByCpu);
        metrics.put("processesByRam", processesByRam);

        messagingTemplate.convertAndSend("/topic/metrics", (Object) metrics);
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