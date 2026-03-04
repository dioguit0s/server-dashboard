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
        double cpuUsage = monitorService.getCpuUsage();
        double ramUsage = monitorService.getMemoryUsagePercentage();
        MonitorService.DiskInfo diskInformation = monitorService.getAdvancedDiskMetrics().overallDiskInfo;
        double cpuTemperature = monitorService.getCpuTemperature();
        String systemUptime = monitorService.getSystemUptime();
        MonitorService.NetworkInfo networkInformation = monitorService.getNetworkMetrics();

        Map<String, Object> publicMetrics = new HashMap<>();
        publicMetrics.put("cpuPercent", String.format("%.1f", cpuUsage));
        publicMetrics.put("cpuInt", (int) cpuUsage);
        publicMetrics.put("ramPercent", String.format("%.1f", ramUsage));
        publicMetrics.put("ramInt", (int) ramUsage);
        long totalMemory = monitorService.getTotalMemory();
        long freeMemory = monitorService.getFreeMemory();
        publicMetrics.put("ramTotal", monitorService.formatMemory(totalMemory));
        publicMetrics.put("ramUsado", monitorService.formatMemory(totalMemory - freeMemory));
        publicMetrics.put("ramLivre", monitorService.formatMemory(freeMemory));

        // ATENÇÃO AQUI: Nomes atualizados sem abreviações
        publicMetrics.put("diskPercent", String.format("%.1f", diskInformation.percentageUsed));
        publicMetrics.put("diskInt", (int) diskInformation.percentageUsed);
        publicMetrics.put("diskTotal", diskInformation.totalSpace);
        publicMetrics.put("diskUsed", diskInformation.usedSpace);
        publicMetrics.put("diskFree", diskInformation.freeSpace);

        publicMetrics.put("cpuTemp", String.format("%.1f", cpuTemperature));
        publicMetrics.put("cpuTempInt", (int) cpuTemperature);
        publicMetrics.put("uptime", systemUptime);
        publicMetrics.put("netDown", networkInformation.downloadRate);
        publicMetrics.put("netUp", networkInformation.uploadRate);

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
        for (var processInfo : list) {
            Map<String, Object> processMap = new HashMap<>();
            processMap.put("name", processInfo.name);
            processMap.put("pid", processInfo.processIdentifier); // Atualizado de p.pid
            processMap.put("cpuPercent", processInfo.cpuPercent);
            processMap.put("ramPercent", processInfo.ramPercent);
            processMap.put("ramFormatted", processInfo.ramFormatted);
            result.add(processMap);
        }
        return result;
    }
}