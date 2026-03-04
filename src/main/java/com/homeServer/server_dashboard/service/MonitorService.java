package com.homeServer.server_dashboard.service;

import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.Sensors;
import oshi.hardware.PhysicalMemory;
import oshi.hardware.HWDiskStore;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.List;
import java.net.InetSocketAddress;
import java.net.Socket;

@Service
public class MonitorService {

    private final SystemInfo systemInformation;
    private final HardwareAbstractionLayer hardwareLayer;
    private final OperatingSystem operatingSystem;
    private final Sensors systemSensors;

    private long previousBytesReceived = 0;
    private long previousBytesSent = 0;

    private long previousBytesReadFromDisk = 0;
    private long previousBytesWrittenToDisk = 0;

    private long currentProcessIdentifier;

    public MonitorService() {
        this.systemInformation = new SystemInfo();
        this.hardwareLayer = systemInformation.getHardware();
        this.operatingSystem = systemInformation.getOperatingSystem();
        this.systemSensors = hardwareLayer.getSensors();
        this.currentProcessIdentifier = ProcessHandle.current().pid();
    }

    public double getCpuTemperature() { return systemSensors.getCpuTemperature(); }
    public String getOsInfo() { return operatingSystem.toString(); }

    public double getMemoryUsagePercentage() {
        GlobalMemory globalMemory = hardwareLayer.getMemory();
        return 100d * (globalMemory.getTotal() - globalMemory.getAvailable()) / globalMemory.getTotal();
    }

    public String formatMemory(long bytesValue) { return formatBytes(bytesValue); }
    public long getTotalMemory() { return hardwareLayer.getMemory().getTotal(); }
    public long getFreeMemory() { return hardwareLayer.getMemory().getAvailable(); }

    // ==========================================
    // MÉTRICAS DE RAM
    // ==========================================
    public SwapInfo getSwapMetrics() {
        long totalSwapMemory = hardwareLayer.getMemory().getVirtualMemory().getSwapTotal();
        long usedSwapMemory = hardwareLayer.getMemory().getVirtualMemory().getSwapUsed();
        long freeSwapMemory = totalSwapMemory - usedSwapMemory;
        return new SwapInfo(formatBytes(totalSwapMemory), formatBytes(usedSwapMemory), formatBytes(freeSwapMemory));
    }

    public List<PhysicalMemoryInfo> getPhysicalMemoryDetails() {
        List<PhysicalMemoryInfo> physicalMemoryInfoList = new ArrayList<>();
        for (PhysicalMemory physicalMemory : hardwareLayer.getMemory().getPhysicalMemory()) {
            physicalMemoryInfoList.add(new PhysicalMemoryInfo(
                    physicalMemory.getBankLabel(),
                    physicalMemory.getManufacturer(),
                    physicalMemory.getMemoryType(),
                    formatBytes(physicalMemory.getCapacity()),
                    (physicalMemory.getClockSpeed() / 1_000_000) + " MHz"
            ));
        }
        return physicalMemoryInfoList;
    }

    public double getCpuUsage() {
        CentralProcessor centralProcessor = hardwareLayer.getProcessor();
        return centralProcessor.getSystemCpuLoad(1000) * 100;
    }

    // ==========================================
    // MÉTRICAS DE CPU
    // ==========================================
    public int getPhysicalProcessorCount() {
        return hardwareLayer.getProcessor().getPhysicalProcessorCount();
    }

    public int getLogicalProcessorCount() {
        return hardwareLayer.getProcessor().getLogicalProcessorCount();
    }

    public String getProcessorVendor() {
        return hardwareLayer.getProcessor().getProcessorIdentifier().getVendor();
    }

    public String getProcessorMicroarchitecture() {
        return hardwareLayer.getProcessor().getProcessorIdentifier().getMicroarchitecture();
    }

    public double getSystemLoadAverage() {
        // Retorna a média de carga do sistema em 1 minuto
        double[] systemLoadAverages = hardwareLayer.getProcessor().getSystemLoadAverage(1);
        return systemLoadAverages[0];
    }

    public String getProcessorName() {
        String processorName = hardwareLayer.getProcessor().getProcessorIdentifier().getName();
        if (processorName == null || processorName.isBlank()) {
            return "Processador desconhecido";
        }
        return processorName.trim();
    }

    public String getProcessorMaximumFrequency() {
        CentralProcessor centralProcessor = hardwareLayer.getProcessor();
        long frequencyHertz = centralProcessor.getProcessorIdentifier().getVendorFreq();
        if (frequencyHertz <= 0) {
            frequencyHertz = centralProcessor.getMaxFreq();
        }
        if (frequencyHertz <= 0) {
            return "N/A";
        }
        double frequencyMegahertz = frequencyHertz / 1_000_000.0;
        if (frequencyMegahertz >= 1000) {
            return String.format("%.2f GHz", frequencyMegahertz / 1000.0);
        }
        return String.format("%.0f MHz", frequencyMegahertz);
    }

    // ==========================================
    // MÉTRICAS DE DISCO ATUALIZADAS
    // ==========================================
    public DiskMetrics getAdvancedDiskMetrics() {
        // 1. Partições / Volumes Lógicos
        List<OSFileStore> fileStoresList = operatingSystem.getFileSystem().getFileStores();
        long totalSpaceAvailable = 0;
        long usableSpaceAvailable = 0;

        List<LogicalVolumeInfo> logicalVolumesList = new ArrayList<>();

        for (OSFileStore fileSystemStore : fileStoresList) {
            if (fileSystemStore.getTotalSpace() > 1024 * 1024 * 1024) { // Ignora partições muito pequenas
                totalSpaceAvailable += fileSystemStore.getTotalSpace();
                usableSpaceAvailable += fileSystemStore.getUsableSpace();

                logicalVolumesList.add(new LogicalVolumeInfo(
                        fileSystemStore.getName(),
                        fileSystemStore.getMount(),
                        fileSystemStore.getType(),
                        formatBytes(fileSystemStore.getTotalSpace()),
                        formatBytes(fileSystemStore.getUsableSpace())
                ));
            }
        }

        double usedPercentageValue = totalSpaceAvailable > 0 ? 100d * (totalSpaceAvailable - usableSpaceAvailable) / totalSpaceAvailable : 0;
        DiskInfo overallDiskInfo = new DiskInfo(
                formatBytes(totalSpaceAvailable),
                formatBytes(totalSpaceAvailable - usableSpaceAvailable),
                formatBytes(usableSpaceAvailable),
                usedPercentageValue
        );

        // 2. Discos Físicos e Leitura/Escrita
        List<HardwareDiskInfo> hardwareDiskInfoList = new ArrayList<>();
        long currentBytesReadFromDisk = 0;
        long currentBytesWrittenToDisk = 0;

        for (HWDiskStore hardwareDiskStore : hardwareLayer.getDiskStores()) {
            hardwareDiskStore.updateAttributes();
            currentBytesReadFromDisk += hardwareDiskStore.getReadBytes();
            currentBytesWrittenToDisk += hardwareDiskStore.getWriteBytes();

            hardwareDiskInfoList.add(new HardwareDiskInfo(
                    hardwareDiskStore.getModel(),
                    hardwareDiskStore.getSerial(),
                    formatBytes(hardwareDiskStore.getSize())
            ));
        }

        // Calcula a taxa por segundo
        if (previousBytesReadFromDisk == 0 || currentBytesReadFromDisk < previousBytesReadFromDisk) {
            previousBytesReadFromDisk = currentBytesReadFromDisk;
            previousBytesWrittenToDisk = currentBytesWrittenToDisk;
        }

        long diskReadSpeedPerSecond = currentBytesReadFromDisk - previousBytesReadFromDisk;
        long diskWriteSpeedPerSecond = currentBytesWrittenToDisk - previousBytesWrittenToDisk;

        previousBytesReadFromDisk = currentBytesReadFromDisk;
        previousBytesWrittenToDisk = currentBytesWrittenToDisk;

        DiskIoInfo diskIoRate = new DiskIoInfo(formatRate(diskReadSpeedPerSecond), formatRate(diskWriteSpeedPerSecond));

        return new DiskMetrics(overallDiskInfo, logicalVolumesList, hardwareDiskInfoList, diskIoRate);
    }

    // Mantido para não quebrar controladores existentes que só buscam a métrica geral
    public DiskInfo getDiskMetrics() {
        return getAdvancedDiskMetrics().overallDiskInfo;
    }

    public NetworkInfo getNetworkMetrics() {
        List<NetworkIF> networkInterfacesList = hardwareLayer.getNetworkIFs();

        long currentBytesReceived = 0;
        long currentBytesSent = 0;

        for (NetworkIF networkInterface : networkInterfacesList) {
            networkInterface.updateAttributes();

            if (networkInterface.getIPv4addr().length > 0 && !networkInterface.getDisplayName().toLowerCase().contains("loopback")) {
                currentBytesReceived += networkInterface.getBytesRecv();
                currentBytesSent += networkInterface.getBytesSent();
            }
        }

        if (previousBytesReceived == 0 || currentBytesReceived < previousBytesReceived) {
            previousBytesReceived = currentBytesReceived;
            previousBytesSent = currentBytesSent;
            return new NetworkInfo("0 B/s", "0 B/s");
        }

        long downloadSpeedPerSecond = currentBytesReceived - previousBytesReceived;
        long uploadSpeedPerSecond = currentBytesSent - previousBytesSent;

        previousBytesReceived = currentBytesReceived;
        previousBytesSent = currentBytesSent;

        return new NetworkInfo(
                formatRate(downloadSpeedPerSecond),
                formatRate(uploadSpeedPerSecond)
        );
    }

    private String formatBytes(long bytesValue) {
        double gigabytesValue = bytesValue / (1024.0 * 1024.0 * 1024.0);
        return String.format("%.2f GB", gigabytesValue);
    }

    private String formatRate(long bytesPerSecondValue) {
        if (bytesPerSecondValue < 1024) return bytesPerSecondValue + " B/s";
        double kilobytesValue = bytesPerSecondValue / 1024.0;
        if (kilobytesValue < 1024) return String.format("%.1f KB/s", kilobytesValue);
        double megabytesValue = kilobytesValue / 1024.0;
        return String.format("%.1f MB/s", megabytesValue);
    }

    public String getSystemUptime() {
        long uptimeInSeconds = operatingSystem.getSystemUptime();
        long uptimeDays = uptimeInSeconds / (24 * 3600);
        long uptimeHours = (uptimeInSeconds % (24 * 3600)) / 3600;
        long uptimeMinutes = (uptimeInSeconds % 3600) / 60;
        long uptimeSecondsRemainder = uptimeInSeconds % 60;
        return String.format("%d dias, %02d:%02d:%02d", uptimeDays, uptimeHours, uptimeMinutes, uptimeSecondsRemainder);
    }

    public boolean isServiceUp(int targetPort){
        try(Socket testSocket = new Socket()) {
            testSocket.connect(new InetSocketAddress("localhost", targetPort), 200);
            return true;
        } catch (Exception connectionException) {
            return false;
        }
    }

    public List<ProcessInfo> getTopProcesses(String sortByValue, int resultLimit) {
        var processSortingComparator = "ram".equalsIgnoreCase(sortByValue)
                ? OperatingSystem.ProcessSorting.RSS_DESC
                : OperatingSystem.ProcessSorting.CPU_DESC;

        List<OSProcess> activeProcessesList = operatingSystem.getProcesses(null, processSortingComparator, resultLimit);
        List<ProcessInfo> topProcessesResultList = new ArrayList<>();

        long totalSystemMemory = hardwareLayer.getMemory().getTotal();
        int logicalProcessorCount = hardwareLayer.getProcessor().getLogicalProcessorCount();

        for (OSProcess currentProcess : activeProcessesList) {
            if (currentProcess == null || currentProcess.getState() == OSProcess.State.INVALID) continue;

            String currentProcessName = currentProcess.getName();
            if(currentProcess.getProcessID() == this.currentProcessIdentifier) {
                currentProcessName = "Dashboard";
            }
            if (currentProcessName == null || currentProcessName.isBlank()) currentProcessName = "(sem nome)";
            if (currentProcessName.length() > 40) currentProcessName = currentProcessName.substring(0, 37) + "...";

            double cpuUsagePercentage = currentProcess.getProcessCpuLoadCumulative() * 100;
            if (logicalProcessorCount > 0) cpuUsagePercentage = Math.min(100, cpuUsagePercentage / logicalProcessorCount);

            long residentSetSizeMemory = currentProcess.getResidentSetSize();
            double ramUsagePercentage = totalSystemMemory > 0 ? 100d * residentSetSizeMemory / totalSystemMemory : 0;

            topProcessesResultList.add(new ProcessInfo(
                    currentProcessName,
                    currentProcess.getProcessID(),
                    String.format("%.1f", cpuUsagePercentage),
                    String.format("%.1f", ramUsagePercentage),
                    formatBytes(residentSetSizeMemory)
            ));
        }
        return topProcessesResultList;
    }

    // ==========================================
    // CLASSES DE ESTRUTURA DE DADOS
    // ==========================================

    public static class ProcessInfo {
        public final String name;
        public final int processIdentifier;
        public final String cpuPercent;
        public final String ramPercent;
        public final String ramFormatted;

        public ProcessInfo(String name, int processIdentifier, String cpuPercent, String ramPercent, String ramFormatted) {
            this.name = name;
            this.processIdentifier = processIdentifier;
            this.cpuPercent = cpuPercent;
            this.ramPercent = ramPercent;
            this.ramFormatted = ramFormatted;
        }
    }

    public static class DiskInfo {
        public String totalSpace, usedSpace, freeSpace;
        public double percentageUsed;
        public DiskInfo(String totalSpace, String usedSpace, String freeSpace, double percentageUsed) {
            this.totalSpace = totalSpace;
            this.usedSpace = usedSpace;
            this.freeSpace = freeSpace;
            this.percentageUsed = percentageUsed;
        }
    }

    public static class NetworkInfo {
        public String downloadRate;
        public String uploadRate;
        public NetworkInfo(String downloadRate, String uploadRate) {
            this.downloadRate = downloadRate;
            this.uploadRate = uploadRate;
        }
    }

    public static class SwapInfo {
        public final String totalSwap;
        public final String usedSwap;
        public final String freeSwap;
        public SwapInfo(String totalSwap, String usedSwap, String freeSwap) {
            this.totalSwap = totalSwap; this.usedSwap = usedSwap; this.freeSwap = freeSwap;
        }
    }

    public static class PhysicalMemoryInfo {
        public final String bankLabel;
        public final String manufacturer;
        public final String memoryType;
        public final String capacity;
        public final String clockSpeed;
        public PhysicalMemoryInfo(String bankLabel, String manufacturer, String memoryType, String capacity, String clockSpeed) {
            this.bankLabel = bankLabel; this.manufacturer = manufacturer; this.memoryType = memoryType;
            this.capacity = capacity; this.clockSpeed = clockSpeed;
        }
    }

    public static class LogicalVolumeInfo {
        public final String volumeName;
        public final String mountPoint;
        public final String fileSystemType;
        public final String totalSpace;
        public final String usableSpace;
        public LogicalVolumeInfo(String volumeName, String mountPoint, String fileSystemType, String totalSpace, String usableSpace) {
            this.volumeName = volumeName; this.mountPoint = mountPoint; this.fileSystemType = fileSystemType;
            this.totalSpace = totalSpace; this.usableSpace = usableSpace;
        }
    }

    public static class HardwareDiskInfo {
        public final String diskModel;
        public final String serialNumber;
        public final String diskSize;
        public HardwareDiskInfo(String diskModel, String serialNumber, String diskSize) {
            this.diskModel = diskModel; this.serialNumber = serialNumber; this.diskSize = diskSize;
        }
    }

    public static class DiskIoInfo {
        public final String readRate;
        public final String writeRate;
        public DiskIoInfo(String readRate, String writeRate) {
            this.readRate = readRate; this.writeRate = writeRate;
        }
    }

    public static class DiskMetrics {
        public final DiskInfo overallDiskInfo;
        public final List<LogicalVolumeInfo> logicalVolumes;
        public final List<HardwareDiskInfo> hardwareDisks;
        public final DiskIoInfo diskIoRate;
        public DiskMetrics(DiskInfo overallDiskInfo, List<LogicalVolumeInfo> logicalVolumes, List<HardwareDiskInfo> hardwareDisks, DiskIoInfo diskIoRate) {
            this.overallDiskInfo = overallDiskInfo; this.logicalVolumes = logicalVolumes;
            this.hardwareDisks = hardwareDisks; this.diskIoRate = diskIoRate;
        }
    }
}