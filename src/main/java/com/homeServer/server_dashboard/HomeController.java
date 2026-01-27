package com.homeServer.server_dashboard;
import com.homeServer.server_dashboard.service.MonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;

@Controller //notacao que diz que é controller
public class HomeController {

    @Autowired
    private MonitorService monitorService;

    @GetMapping("/") //mapeia a URL raiz (http://localhost:8080/)
    public String index(Model model) {
        String so = monitorService.getOsInfo();
        String ramTotal = monitorService.getTotalMemory();
        String ramLivre = monitorService.getFreeMemory();
        String cpu = monitorService.getCpuUsage();

        model.addAttribute("sistemaOperacional", so);
        model.addAttribute("memoriaTotal", ramTotal);
        model.addAttribute("memoriaLivre", ramLivre);
        model.addAttribute("usoCpu", cpu);

        return "home";
    }
}
