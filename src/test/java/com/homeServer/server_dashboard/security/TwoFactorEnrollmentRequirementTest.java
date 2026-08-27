package com.homeServer.server_dashboard.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import com.homeServer.server_dashboard.model.DashboardRole;
import com.homeServer.server_dashboard.model.DashboardUser;

/**
 * Com {@code dashboard.security.totp.required-for-admin=true}, um ADMIN sem 2FA fica preso na tela
 * da propria conta ate concluir o enrollment — e, principalmente, nao consegue executar acoes de
 * escrita nesse meio-tempo.
 */
@SpringBootTest(properties = "dashboard.security.totp.required-for-admin=true")
@AutoConfigureMockMvc
class TwoFactorEnrollmentRequirementTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * O filtro so' reconhece o principal do proprio dashboard, entao o token de teste carrega um
     * {@link DashboardUserDetails} de verdade.
     */
    private static Authentication adminWithout2fa() {
        DashboardUser user = new DashboardUser("chefe", "irrelevante", EnumSet.of(DashboardRole.ADMIN));
        DashboardUserDetails details = new DashboardUserDetails(user);
        return UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
    }

    private static Authentication adminWith2fa() {
        DashboardUser user = new DashboardUser("chefe", "irrelevante", EnumSet.of(DashboardRole.ADMIN));
        user.setTotpEnabled(true);
        DashboardUserDetails details = new DashboardUserDetails(user);
        return UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
    }

    private static Authentication viewerWithout2fa() {
        DashboardUser user = new DashboardUser("observador", "irrelevante", EnumSet.of(DashboardRole.VIEWER));
        DashboardUserDetails details = new DashboardUserDetails(user);
        return UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
    }

    @Test
    void sendsAnAdminWithoutTwoFactorToTheEnrollmentPage() throws Exception {
        mockMvc.perform(get("/containers").with(authentication(adminWithout2fa())))
                .andExpect(redirectedUrl("/account/2fa"));
        mockMvc.perform(get("/admin/users").with(authentication(adminWithout2fa())))
                .andExpect(redirectedUrl("/account/2fa"));
    }

    @Test
    void leavesTheAccountPageReachableSoTheEnrollmentCanBeCompleted() throws Exception {
        mockMvc.perform(get("/account/2fa").with(authentication(adminWithout2fa())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/account/2fa/enrollment").with(authentication(adminWithout2fa())).with(csrf()))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }

    /**
     * O redirecionamento nao pode ser so' cosmetico: escrever via API tambem tem de ser recusado
     * enquanto o 2FA nao estiver ativo.
     */
    @Test
    void blocksWriteActionsUntilTheEnrollmentIsDone() throws Exception {
        mockMvc.perform(post("/api/docker/stop/abc123456789").with(authentication(adminWithout2fa())).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void doesNotGetInTheWayOfAnAdminThatAlreadyEnrolled() throws Exception {
        mockMvc.perform(get("/containers").with(authentication(adminWith2fa())))
                .andExpect(status().isOk());
    }

    /**
     * A exigencia e' de ADMIN: quem so' le nao e' empurrado para o enrollment.
     */
    @Test
    void doesNotApplyToViewers() throws Exception {
        mockMvc.perform(get("/containers").with(authentication(viewerWithout2fa())))
                .andExpect(status().isOk());
    }
}
