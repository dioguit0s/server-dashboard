package com.homeServer.server_dashboard.service;

import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.Sensors;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.util.List;

@Service
public class MonitorService {

    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem os;
    private final Sensors sensors;

    public MonitorService() {
        this.systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.os = systemInfo.getOperatingSystem();
        this.sensors = hardware.getSensors();
    }

    public double getCpuTemperature() {
        return sensors.getCpuTemperature();
    }

    public String getOsInfo() {
        return os.toString();
    }

    // Retorna um objeto simples com dados de memória para facilitar no front
    public double getMemoryUsagePercentage() {
        GlobalMemory memory = hardware.getMemory();
        long total = memory.getTotal();
        long available = memory.getAvailable();
        return 100d * (total - available) / total;
    }

    public String formatMemory(long bytes) {
        return formatBytes(bytes);
    }

    public long getTotalMemory() {
        return hardware.getMemory().getTotal();
    }

    public long getFreeMemory() {
        return hardware.getMemory().getAvailable();
    }

    public double getCpuUsage() {
        CentralProcessor processor = hardware.getProcessor();
        // O delay de 1000ms é necessário para calcular o delta de uso
        return processor.getSystemCpuLoad(1000) * 100;
    }

    // Nova funcionalidade: Monitoramento de Disco
    public DiskInfo getDiskMetrics() {
        List<OSFileStore> fileStores = os.getFileSystem().getFileStores();

        long totalSpace = 0;
        long usableSpace = 0;

        for (OSFileStore fs : fileStores) {
            // Filtra partições virtuais ou pequenas demais para evitar sujeira visual
            if (fs.getTotalSpace() > 1024 * 1024 * 1024) {
                totalSpace += fs.getTotalSpace();
                usableSpace += fs.getUsableSpace();
            }
        }

        long usedSpace = totalSpace - usableSpace;
        double usagePercent = 100d * usedSpace / totalSpace;

        return new DiskInfo(formatBytes(totalSpace), formatBytes(usedSpace), formatBytes(usableSpace), usagePercent);
    }

    private String formatBytes(long bytes) {
        double gigabytes = bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format("%.2f GB", gigabytes);
    }

    public String getSystemUptime() {
        long uptimeSeconds = os.getSystemUptime();

        long days = uptimeSeconds / (24 * 3600);
        long remainder = uptimeSeconds % (24 * 3600);
        long hours = remainder / 3600;
        remainder %= 3600;
        long minutes = remainder / 60;
        long seconds = remainder % 60;

        // Retorna algo como "5 dias, 12:30:45"
        return String.format("%d dias, %02d:%02d:%02d", days, hours, minutes, seconds);
    }

    // Classe interna auxiliar para transportar dados do disco (DTO)
    public static class DiskInfo {
        public String total;
        public String used;
        public String free;
        public double percent;

        public DiskInfo(String total, String used, String free, double percent) {
            this.total = total;
            this.used = used;
            this.free = free;
            this.percent = percent;
        }
    }
}