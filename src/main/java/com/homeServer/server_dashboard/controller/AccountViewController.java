package com.homeServer.server_dashboard.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.homeServer.server_dashboard.security.DashboardUserDetails;
import com.homeServer.server_dashboard.service.MonitorService;
import com.homeServer.server_dashboard.service.TwoFactorService;

/**
 * Paginas da area logada que nao existiam antes: a conta do proprio usuario e a gestao de usuarios.
 */
@Controller
public class AccountViewController {

    private final MonitorService monitorService;
    private final TwoFactorService twoFactorService;

    public AccountViewController(MonitorService monitorService, TwoFactorService twoFactorService) {
        this.monitorService = monitorService;
        this.twoFactorService = twoFactorService;
    }

    @GetMapping({"/account", "/account/2fa"})
    public String account(Model model, @AuthenticationPrincipal UserDetails user) {
        boolean totpEnabled = user instanceof DashboardUserDetails details && details.isTotpEnabled();

        model.addAttribute("osName", monitorService.getOsInfo());
        model.addAttribute("totpEnabled", totpEnabled);
        model.addAttribute("unusedRecoveryCodes",
                totpEnabled ? twoFactorService.unusedRecoveryCodeCount(user.getUsername()) : 0L);
        return "home/account";
    }

    @GetMapping("/admin/users")
    public String users(Model model) {
        model.addAttribute("osName", monitorService.getOsInfo());
        return "home/users";
    }
}
