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
        // Coleta de dados
        double cpuDouble = monitorService.getCpuUsage();
        double ramDouble = monitorService.getMemoryUsagePercentage();
        MonitorService.DiskInfo diskInfo = monitorService.getDiskMetrics();

        // Dados puros para exibição
        model.addAttribute("osName", monitorService.getOsInfo());

        // Dados de CPU
        model.addAttribute("cpuPercent", String.format("%.1f", cpuDouble));
        model.addAttribute("cpuInt", (int) cpuDouble); // Para a barra de progresso (CSS width)

        // Dados de RAM
        model.addAttribute("ramPercent", String.format("%.1f", ramDouble));
        model.addAttribute("ramInt", (int) ramDouble);
        model.addAttribute("ramTotal", monitorService.formatMemory(monitorService.getTotalMemory()));
        model.addAttribute("ramLivre", monitorService.formatMemory(monitorService.getFreeMemory()));

        // Dados de Disco
        model.addAttribute("diskTotal", diskInfo.total);
        model.addAttribute("diskUsed", diskInfo.used);
        model.addAttribute("diskFree", diskInfo.free);
        model.addAttribute("diskPercent", String.format("%.1f", diskInfo.percent));
        model.addAttribute("diskInt", (int) diskInfo.percent);

        return "home";
    }
}