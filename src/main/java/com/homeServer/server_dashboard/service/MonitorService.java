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
import java.util.Comparator;
import java.util.List;
import java.net.InetSocketAddress;
import java.net.Socket;

@Service
public class MonitorService {

    private final SystemInfo systemInformation;
    private final HardwareAbstractionLayer hardwareLayer;
    private final OperatingSystem operatingSystem;
    private final Sensors systemSensors;

    /**
     * Amostras pedidas em sequencia mais rapida que isto reaproveitam o ultimo valor calculado.
     * Uma taxa medida sobre uma janela de poucos milissegundos nao tem significado, e a coleta
     * (varrer interfaces de rede, chamar updateAttributes em cada disco) nao e' de graca num
     * servidor pequeno: a home renderiza no mesmo segundo em que o broadcast periodico roda.
     */
    private static final long MINIMUM_SAMPLE_INTERVAL_MILLISECONDS = 500;

    private long[] previousCpuTicks;
    private double lastCpuLoadPercentage = 0;
    private long lastCpuSampleAtMillis = 0;

    private long previousBytesReceived = 0;
    private long previousBytesSent = 0;
    private long previousNetworkSampleAtNanos = 0;
    private NetworkInfo lastNetworkInfo = new NetworkInfo("0 B/s", "0 B/s");
    private long lastNetworkSampleAtMillis = 0;

    private long previousBytesReadFromDisk = 0;
    private long previousBytesWrittenToDisk = 0;
    private long previousDiskSampleAtNanos = 0;
    private DiskMetrics lastDiskMetrics = null;
    private long lastDiskSampleAtMillis = 0;

    private long currentProcessIdentifier;

    public MonitorService() {
        this.systemInformation = new SystemInfo();
        this.hardwareLayer = systemInformation.getHardware();
        this.operatingSystem = systemInformation.getOperatingSystem();
        this.systemSensors = hardwareLayer.getSensors();
        this.currentProcessIdentifier = ProcessHandle.current().pid();
        this.previousCpuTicks = hardwareLayer.getProcessor().getSystemCpuLoadTicks();
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

    /**
     * Carga de CPU do sistema, calculada pelos ticks acumulados desde a amostra anterior.
     *
     * <p>A alternativa da OSHI, {@code getSystemCpuLoad(1000)}, bloqueia a thread chamadora pelo
     * intervalo inteiro. Como este metodo e' chamado a cada segundo pelo broadcast, a cada minuto
     * pelo historico e a cada renderizacao da home, aquele bloqueio mantinha uma thread do
     * scheduler ocupada em tempo integral — e fazia cada carregamento de pagina esperar 1s.
     */
    public synchronized double getCpuUsage() {
        long nowMillis = System.currentTimeMillis();
        if (lastCpuSampleAtMillis != 0 && nowMillis - lastCpuSampleAtMillis < MINIMUM_SAMPLE_INTERVAL_MILLISECONDS) {
            return lastCpuLoadPercentage;
        }
        CentralProcessor centralProcessor = hardwareLayer.getProcessor();
        lastCpuLoadPercentage = centralProcessor.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100;
        previousCpuTicks = centralProcessor.getSystemCpuLoadTicks();
        lastCpuSampleAtMillis = nowMillis;
        return lastCpuLoadPercentage;
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
        double[] systemLoadAverages = hardwareLayer.getProcessor().getSystemLoadAverage(1);
        if (systemLoadAverages == null || systemLoadAverages.length == 0) {
            return -1;
        }
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
    public synchronized DiskMetrics getAdvancedDiskMetrics() {
        long nowMillis = System.currentTimeMillis();
        if (lastDiskMetrics != null && nowMillis - lastDiskSampleAtMillis < MINIMUM_SAMPLE_INTERVAL_MILLISECONDS) {
            return lastDiskMetrics;
        }

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

        // Calcula a taxa dividindo pelo tempo realmente decorrido desde a amostra anterior. A
        // versao antiga assumia exatamente 1s entre chamadas, o que passou a ser falso quando as
        // coletas deixaram de rodar de forma continua (agora so' ha broadcast com alguem assinando).
        long nowNanos = System.nanoTime();
        DiskIoInfo diskIoRate = computeIoRate(
                currentBytesReadFromDisk, currentBytesWrittenToDisk, nowNanos);

        previousBytesReadFromDisk = currentBytesReadFromDisk;
        previousBytesWrittenToDisk = currentBytesWrittenToDisk;
        previousDiskSampleAtNanos = nowNanos;

        lastDiskMetrics = new DiskMetrics(overallDiskInfo, logicalVolumesList, hardwareDiskInfoList, diskIoRate);
        lastDiskSampleAtMillis = nowMillis;
        return lastDiskMetrics;
    }

    private DiskIoInfo computeIoRate(long currentBytesRead, long currentBytesWritten, long nowNanos) {
        double elapsedSeconds = elapsedSecondsSince(previousDiskSampleAtNanos, nowNanos);
        boolean countersRestarted = currentBytesRead < previousBytesReadFromDisk
                || currentBytesWritten < previousBytesWrittenToDisk;
        if (elapsedSeconds <= 0 || countersRestarted) {
            return new DiskIoInfo("0 B/s", "0 B/s");
        }
        return new DiskIoInfo(
                formatRate((long) ((currentBytesRead - previousBytesReadFromDisk) / elapsedSeconds)),
                formatRate((long) ((currentBytesWritten - previousBytesWrittenToDisk) / elapsedSeconds)));
    }

    /** Segundos entre duas leituras de {@link System#nanoTime()}; 0 quando ainda nao ha anterior. */
    private double elapsedSecondsSince(long previousSampleAtNanos, long nowNanos) {
        if (previousSampleAtNanos == 0) {
            return 0;
        }
        return (nowNanos - previousSampleAtNanos) / 1_000_000_000d;
    }

    // Mantido para não quebrar controladores existentes que só buscam a métrica geral
    public DiskInfo getDiskMetrics() {
        return getAdvancedDiskMetrics().overallDiskInfo;
    }

    public synchronized NetworkInfo getNetworkMetrics() {
        long nowMillis = System.currentTimeMillis();
        if (lastNetworkSampleAtMillis != 0 && nowMillis - lastNetworkSampleAtMillis < MINIMUM_SAMPLE_INTERVAL_MILLISECONDS) {
            return lastNetworkInfo;
        }

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

        long nowNanos = System.nanoTime();
        double elapsedSeconds = elapsedSecondsSince(previousNetworkSampleAtNanos, nowNanos);
        boolean countersRestarted = currentBytesReceived < previousBytesReceived
                || currentBytesSent < previousBytesSent;

        if (elapsedSeconds <= 0 || countersRestarted) {
            lastNetworkInfo = new NetworkInfo("0 B/s", "0 B/s");
        } else {
            lastNetworkInfo = new NetworkInfo(
                    formatRate((long) ((currentBytesReceived - previousBytesReceived) / elapsedSeconds)),
                    formatRate((long) ((currentBytesSent - previousBytesSent) / elapsedSeconds))
            );
        }

        previousBytesReceived = currentBytesReceived;
        previousBytesSent = currentBytesSent;
        previousNetworkSampleAtNanos = nowNanos;
        lastNetworkSampleAtMillis = nowMillis;
        return lastNetworkInfo;
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

    /**
     * Os processos que mais consomem recursos, ordenados por CPU e por RAM, a partir de uma unica
     * varredura.
     *
     * <p>{@code OperatingSystem.getProcesses(filtro, ordem, limite)} monta o objeto de <b>todos</b>
     * os processos antes de ordenar e cortar — o limite nao evita a leitura de /proc. Pedir as duas
     * ordenacoes em chamadas separadas custava duas varreduras completas por segundo; aqui a mesma
     * lista e' ordenada duas vezes, o que e' irrisorio perto do custo de ler /proc.
     */
    public TopProcesses getTopProcesses(int resultLimit) {
        List<OSProcess> allProcessesList = operatingSystem.getProcesses(null, null, 0);

        List<OSProcess> validProcessesList = new ArrayList<>(allProcessesList.size());
        for (OSProcess currentProcess : allProcessesList) {
            if (currentProcess != null && currentProcess.getState() != OSProcess.State.INVALID) {
                validProcessesList.add(currentProcess);
            }
        }

        long totalSystemMemory = hardwareLayer.getMemory().getTotal();
        int logicalProcessorCount = hardwareLayer.getProcessor().getLogicalProcessorCount();

        return new TopProcesses(
                toTopProcessInfoList(validProcessesList, OperatingSystem.ProcessSorting.CPU_DESC,
                        resultLimit, totalSystemMemory, logicalProcessorCount),
                toTopProcessInfoList(validProcessesList, OperatingSystem.ProcessSorting.RSS_DESC,
                        resultLimit, totalSystemMemory, logicalProcessorCount));
    }

    private List<ProcessInfo> toTopProcessInfoList(List<OSProcess> processesList,
                                                   Comparator<OSProcess> sortingComparator,
                                                   int resultLimit,
                                                   long totalSystemMemory,
                                                   int logicalProcessorCount) {
        List<OSProcess> sortedProcessesList = new ArrayList<>(processesList);
        sortedProcessesList.sort(sortingComparator);

        int effectiveLimit = Math.min(Math.max(resultLimit, 0), sortedProcessesList.size());
        List<ProcessInfo> topProcessesResultList = new ArrayList<>(effectiveLimit);

        for (OSProcess currentProcess : sortedProcessesList.subList(0, effectiveLimit)) {
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

    /** As duas ordenacoes produzidas por uma unica varredura de processos. */
    public record TopProcesses(List<ProcessInfo> byCpuUsage, List<ProcessInfo> byRamUsage) {}

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