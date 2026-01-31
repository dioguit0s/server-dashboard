package com.homeServer.server_dashboard.service;

import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.Sensors;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
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