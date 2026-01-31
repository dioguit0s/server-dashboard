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

    // Rota para a Página Principal (Dashboard)
    @GetMapping("/")
    public String index(Model model) {
        // Coleta de dados iniciais
        double cpuDouble = monitorService.getCpuUsage();
        double ramDouble = monitorService.getMemoryUsagePercentage();
        MonitorService.DiskInfo diskInfo = monitorService.getDiskMetrics();
        double tempDouble = monitorService.getCpuTemperature();

        // Passando dados para o Template
        model.addAttribute("osName", monitorService.getOsInfo());
        model.addAttribute("cpuPercent", String.format("%.1f", cpuDouble));
        model.addAttribute("cpuInt", (int) cpuDouble);
        model.addAttribute("ramPercent", String.format("%.1f", ramDouble));
        model.addAttribute("ramInt", (int) ramDouble);
        model.addAttribute("ramTotal", monitorService.formatMemory(monitorService.getTotalMemory()));
        model.addAttribute("ramLivre", monitorService.formatMemory(monitorService.getFreeMemory()));
        model.addAttribute("diskTotal", diskInfo.total);
        model.addAttribute("diskUsed", diskInfo.used);
        model.addAttribute("diskFree", diskInfo.free);
        model.addAttribute("diskPercent", String.format("%.1f", diskInfo.percent));
        model.addAttribute("diskInt", (int) diskInfo.percent);
        model.addAttribute("cpuTemp", String.format("%.1f", tempDouble));
        model.addAttribute("cpuTempInt", (int) tempDouble);
        model.addAttribute("uptime", monitorService.getSystemUptime());

        return "home/home";
    }

    @GetMapping("/charts")
    public String charts(Model model) {
        model.addAttribute("osName", monitorService.getOsInfo());

        return "home/charts";
    }

    @GetMapping("/services")
    public String services(Model model) {
        model.addAttribute("osName", monitorService.getOsInfo());
        return "home/services";
    }
}