package com.homeServer.server_dashboard.service;

import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.Sensors;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.List;
import java.net.InetSocketAddress;
import java.net.Socket;

@Service
public class MonitorService {

    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem os;
    private final Sensors sensors;

    private long prevBytesRecv = 0;
    private long prevBytesSent = 0;

    public MonitorService() {
        this.systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.os = systemInfo.getOperatingSystem();
        this.sensors = hardware.getSensors();
    }

    public double getCpuTemperature() { return sensors.getCpuTemperature(); }
    public String getOsInfo() { return os.toString(); }

    public double getMemoryUsagePercentage() {
        GlobalMemory memory = hardware.getMemory();
        return 100d * (memory.getTotal() - memory.getAvailable()) / memory.getTotal();
    }

    public String formatMemory(long bytes) { return formatBytes(bytes); }
    public long getTotalMemory() { return hardware.getMemory().getTotal(); }
    public long getFreeMemory() { return hardware.getMemory().getAvailable(); }

    public double getCpuUsage() {
        CentralProcessor processor = hardware.getProcessor();
        return processor.getSystemCpuLoad(1000) * 100;
    }

    public DiskInfo getDiskMetrics() {
        List<OSFileStore> fileStores = os.getFileSystem().getFileStores();
        long totalSpace = 0;
        long usableSpace = 0;
        for (OSFileStore fs : fileStores) {
            if (fs.getTotalSpace() > 1024 * 1024 * 1024) {
                totalSpace += fs.getTotalSpace();
                usableSpace += fs.getUsableSpace();
            }
        }
        return new DiskInfo(formatBytes(totalSpace), formatBytes(totalSpace - usableSpace), formatBytes(usableSpace), 100d * (totalSpace - usableSpace) / totalSpace);
    }

    public NetworkInfo getNetworkMetrics() {
        List<NetworkIF> networkIFs = hardware.getNetworkIFs();

        long currentBytesRecv = 0;
        long currentBytesSent = 0;

        for (NetworkIF net : networkIFs) {
            // Atualiza os atributos da interface
            net.updateAttributes();

            // FILTRO: Só processa se tiver IPv4 (conectada) e não for Loopback (localhost)
            // Isso ignora adaptadores Bluetooth vazios, Docker inativo, etc.
            if (net.getIPv4addr().length > 0 && !net.getDisplayName().toLowerCase().contains("loopback")) {
                currentBytesRecv += net.getBytesRecv();
                currentBytesSent += net.getBytesSent();

                // Opcional: Descomente para ver no console qual placa está sendo lida
                // System.out.println("Lendo interface: " + net.getDisplayName());
            }
        }

        // Se for a primeira execução ou os bytes zerarem (reinício de interface), reseta
        if (prevBytesRecv == 0 || currentBytesRecv < prevBytesRecv) {
            prevBytesRecv = currentBytesRecv;
            prevBytesSent = currentBytesSent;
            return new NetworkInfo("0 B/s", "0 B/s");
        }

        long downloadSpeed = currentBytesRecv - prevBytesRecv;
        long uploadSpeed = currentBytesSent - prevBytesSent;

        // Atualiza o "passado" para a próxima execução
        prevBytesRecv = currentBytesRecv;
        prevBytesSent = currentBytesSent;

        return new NetworkInfo(
                formatRate(downloadSpeed),
                formatRate(uploadSpeed)
        );
    }

    private String formatBytes(long bytes) {
        double gigabytes = bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format("%.2f GB", gigabytes);
    }

    private String formatRate(long bytesPerSecond) {
        if (bytesPerSecond < 1024) return bytesPerSecond + " B/s";
        double kb = bytesPerSecond / 1024.0;
        if (kb < 1024) return String.format("%.1f KB/s", kb);
        double mb = kb / 1024.0;
        return String.format("%.1f MB/s", mb);
    }

    public String getSystemUptime() {
        long uptimeSeconds = os.getSystemUptime();
        long days = uptimeSeconds / (24 * 3600);
        long hours = (uptimeSeconds % (24 * 3600)) / 3600;
        long minutes = (uptimeSeconds % 3600) / 60;
        long seconds = uptimeSeconds % 60;
        return String.format("%d dias, %02d:%02d:%02d", days, hours, minutes, seconds);
    }

    public boolean isServiceUp(int port){
        try(Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 200);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Retorna os top N processos ordenados por CPU ou memória (RSS).
     * @param sortBy "cpu" ou "ram"
     * @param limit quantidade máxima (ex: 15)
     */
    public List<ProcessInfo> getTopProcesses(String sortBy, int limit) {
        var comparator = "ram".equalsIgnoreCase(sortBy)
                ? OperatingSystem.ProcessSorting.RSS_DESC
                : OperatingSystem.ProcessSorting.CPU_DESC;

        List<OSProcess> procs = os.getProcesses(null, comparator, limit);
        List<ProcessInfo> result = new ArrayList<>();

        long totalMem = hardware.getMemory().getTotal();
        int cpuCount = hardware.getProcessor().getLogicalProcessorCount();

        for (OSProcess p : procs) {
            if (p == null || p.getState() == OSProcess.State.INVALID) continue;

            String name = p.getName();
            if (name == null || name.isBlank()) name = "(sem nome)";
            if (name.length() > 40) name = name.substring(0, 37) + "...";

            double cpuPercent = p.getProcessCpuLoadCumulative() * 100;
            if (cpuCount > 0) cpuPercent = Math.min(100, cpuPercent / cpuCount);

            long rss = p.getResidentSetSize();
            double ramPercent = totalMem > 0 ? 100d * rss / totalMem : 0;

            result.add(new ProcessInfo(
                    name,
                    p.getProcessID(),
                    String.format("%.1f", cpuPercent),
                    String.format("%.1f", ramPercent),
                    formatBytes(rss)
            ));
        }
        return result;
    }

    public static class ProcessInfo {
        public final String name;
        public final int pid;
        public final String cpuPercent;
        public final String ramPercent;
        public final String ramFormatted;

        public ProcessInfo(String name, int pid, String cpuPercent, String ramPercent, String ramFormatted) {
            this.name = name;
            this.pid = pid;
            this.cpuPercent = cpuPercent;
            this.ramPercent = ramPercent;
            this.ramFormatted = ramFormatted;
        }
    }

    public static class DiskInfo {
        public String total, used, free;
        public double percent;
        public DiskInfo(String t, String u, String f, double p) { total = t; used = u; free = f; percent = p; }
    }

    public static class NetworkInfo {
        public String downloadRate;
        public String uploadRate;
        public NetworkInfo(String down, String up) { this.downloadRate = down; this.uploadRate = up; }
    }
}