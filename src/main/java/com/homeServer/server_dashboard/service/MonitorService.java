package com.homeServer.server_dashboard.service;

import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

@Service
public class MonitorService {

    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem os;

    public MonitorService() {
        this.systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.os = systemInfo.getOperatingSystem();
    }

    //busca informacoes basicas do sistema operacional
    public String getOsInfo(){
        return os.toString();
    }

    //pega a RAM total do sistema
    public String getTotalMemory() {
        GlobalMemory memory = hardware.getMemory();
        long totalBytes = memory.getTotal();
        return formatBytes(totalBytes);
    }

    //metodo para pegar a ram disponivel
    public String getFreeMemory() {
        GlobalMemory memory = hardware.getMemory();
        long availableBytes = memory.getAvailable();
        return formatBytes(availableBytes);
    }

    //Uso da CPU
    public String getCpuUsage() {
        CentralProcessor processor = hardware.getProcessor();

        double[] load = processor.getSystemLoadAverage(1);
        double cpuLoad = processor.getSystemCpuLoad(1000) * 100;

        return String.format("%.2f %%" , cpuLoad);
    }

    // Função auxiliar para formatar bytes em GB
    private String formatBytes(long bytes) {
        double gigabytes = bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format("%.2f GB", gigabytes);
    }
}
