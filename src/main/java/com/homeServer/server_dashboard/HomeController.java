package com.homeServer.server_dashboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import com.homeServer.server_dashboard.service.MonitorService;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @Autowired
    private MonitorService monitorService;

    @GetMapping("/")
    public String index(Model model) {
        double cpuUsage = monitorService.getCpuUsage();
        double ramUsage = monitorService.getMemoryUsagePercentage();
        MonitorService.DiskInfo diskInformation = monitorService.getAdvancedDiskMetrics().overallDiskInfo;
        double cpuTemperature = monitorService.getCpuTemperature();

        model.addAttribute("osName", monitorService.getOsInfo());
        model.addAttribute("cpuPercent", String.format("%.1f", cpuUsage));
        model.addAttribute("cpuInt", (int) cpuUsage);
        model.addAttribute("ramPercent", String.format("%.1f", ramUsage));
        model.addAttribute("ramInt", (int) ramUsage);
        model.addAttribute("ramTotal", monitorService.formatMemory(monitorService.getTotalMemory()));
        model.addAttribute("ramLivre", monitorService.formatMemory(monitorService.getFreeMemory()));

        model.addAttribute("diskTotal", diskInformation.totalSpace);
        model.addAttribute("diskUsed", diskInformation.usedSpace);
        model.addAttribute("diskFree", diskInformation.freeSpace);
        model.addAttribute("diskPercent", String.format("%.1f", diskInformation.percentageUsed));
        model.addAttribute("diskInt", (int) diskInformation.percentageUsed);

        model.addAttribute("cpuTemp", String.format("%.1f", cpuTemperature));
        model.addAttribute("cpuTempInt", (int) cpuTemperature);
        model.addAttribute("uptime", monitorService.getSystemUptime());

        return "home/home";
    }

    @GetMapping("/login")
    public String login() {
        return "home/login";
    }

    @GetMapping("/charts")
    public String charts(Model model) {
        model.addAttribute("osName", monitorService.getOsInfo());
        return "home/charts";
    }

    @GetMapping("/cpu-details")
    public String cpuDetails(Model model) {
        model.addAttribute("osName", monitorService.getOsInfo());
        model.addAttribute("processorName", monitorService.getProcessorName());
        model.addAttribute("processorMaximumFrequency", monitorService.getProcessorMaximumFrequency());
        model.addAttribute("physicalCores", monitorService.getPhysicalProcessorCount());
        model.addAttribute("logicalCores", monitorService.getLogicalProcessorCount());
        model.addAttribute("processorVendor", monitorService.getProcessorVendor());
        model.addAttribute("processorMicroarchitecture", monitorService.getProcessorMicroarchitecture());
        model.addAttribute("systemLoadAverage", String.format("%.2f", monitorService.getSystemLoadAverage()));
        return "home/cpu-details";
    }

    @GetMapping("/disk-details")
    public String diskDetails(Model model) {
        MonitorService.DiskMetrics advancedDiskMetrics = monitorService.getAdvancedDiskMetrics();
        MonitorService.DiskInfo diskInformation = advancedDiskMetrics.overallDiskInfo;

        model.addAttribute("osName", monitorService.getOsInfo());
        model.addAttribute("diskTotal", diskInformation.totalSpace);
        model.addAttribute("diskUsed", diskInformation.usedSpace);
        model.addAttribute("diskFree", diskInformation.freeSpace);
        model.addAttribute("diskPercent", String.format("%.1f", diskInformation.percentageUsed));
        model.addAttribute("diskInt", (int) diskInformation.percentageUsed);

        // Novas listas para o front
        model.addAttribute("hardwareDisksList", advancedDiskMetrics.hardwareDisks);
        model.addAttribute("logicalVolumesList", advancedDiskMetrics.logicalVolumes);
        return "home/disk-details";
    }

    @GetMapping("/ram-details")
    public String ramDetails(Model model) {
        double ramUsagePercentage = monitorService.getMemoryUsagePercentage();
        long totalMemory = monitorService.getTotalMemory();
        long freeMemory = monitorService.getFreeMemory();

        model.addAttribute("osName", monitorService.getOsInfo());
        model.addAttribute("ramPercent", String.format("%.1f", ramUsagePercentage));
        model.addAttribute("ramInt", (int) ramUsagePercentage);
        model.addAttribute("ramTotal", monitorService.formatMemory(totalMemory));
        model.addAttribute("ramUsado", monitorService.formatMemory(totalMemory - freeMemory));
        model.addAttribute("ramLivre", monitorService.formatMemory(freeMemory));

        // Novos dados de Swap e Física
        model.addAttribute("swapInformation", monitorService.getSwapMetrics());
        model.addAttribute("physicalMemoryList", monitorService.getPhysicalMemoryDetails());
        return "home/ram-details";
    }

    @GetMapping("/services")
    public String services(Model model) {
        model.addAttribute("osName", monitorService.getOsInfo());
        return "home/services";
    }

    @GetMapping("/processes")
    public String processes(Model model) {
        model.addAttribute("osName", monitorService.getOsInfo());
        return "home/processes";
    }
}