package com.homeServer.server_dashboard.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O criterio central da issue #20: leitura e escrita passam a ser papeis diferentes.
 *
 * <p>VIEWER continua vendo tudo o que era visivel na area logada, mas nenhum endpoint que mexe no
 * servidor — parar container, editar a lista de servicos — responde para ele.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleAuthorizationIntegrationTest {

    private static final String SOME_CONTAINER_ID = "abc123456789";

    @Autowired
    private MockMvc mockMvc;

    /** ADMIN implica VIEWER (ver {@code DashboardRole}), entao o token de teste carrega os dois. */
    private static UserRequestPostProcessor admin() {
        return user("chefe").roles("ADMIN", "VIEWER");
    }

    private static UserRequestPostProcessor viewer() {
        return user("observador").roles("VIEWER");
    }

    // --- Leitura: VIEWER basta -------------------------------------------

    @Test
    void viewerReadsTheContainerList() throws Exception {
        mockMvc.perform(get("/api/docker/containers").with(viewer())).andExpect(status().isOk());
    }

    @Test
    void viewerReadsTheMonitoredServices() throws Exception {
        mockMvc.perform(get("/api/services").with(viewer())).andExpect(status().isOk());
    }

    @Test
    void viewerOpensTheProtectedPages() throws Exception {
        mockMvc.perform(get("/containers").with(viewer())).andExpect(status().isOk());
        mockMvc.perform(get("/logs").with(viewer())).andExpect(status().isOk());
        mockMvc.perform(get("/services").with(viewer())).andExpect(status().isOk());
        mockMvc.perform(get("/processes").with(viewer())).andExpect(status().isOk());
    }

    // --- Escrita: so' ADMIN ----------------------------------------------

    @Test
    void viewerCannotActOnContainers() throws Exception {
        for (String action : new String[]{"start", "stop", "restart"}) {
            mockMvc.perform(post("/api/docker/" + action + "/" + SOME_CONTAINER_ID).with(viewer()).with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void viewerCannotChangeTheMonitoredServices() throws Exception {
        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"teste\",\"port\":9999}")
                        .with(viewer()).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/services/9999").with(viewer()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotReachTheUserManagement() throws Exception {
        mockMvc.perform(get("/admin/users").with(viewer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").with(viewer())).andExpect(status().isForbidden());
    }

    @Test
    void adminReachesTheWriteEndpoints() throws Exception {
        // Sem Docker no ambiente de teste a acao em si falha; o que importa aqui e' que ela passa
        // da autorizacao, ou seja: qualquer coisa menos 403.
        mockMvc.perform(post("/api/docker/stop/" + SOME_CONTAINER_ID).with(admin()).with(csrf()))
                .andExpect(status().is(Matchers.not(403)));

        mockMvc.perform(delete("/api/services/9999").with(admin()).with(csrf()))
                .andExpect(status().is(Matchers.not(403)));
    }

    @Test
    void adminAlsoReadsWhatAViewerReads() throws Exception {
        mockMvc.perform(get("/api/docker/containers").with(admin())).andExpect(status().isOk());
        mockMvc.perform(get("/containers").with(admin())).andExpect(status().isOk());
        mockMvc.perform(get("/admin/users").with(admin())).andExpect(status().isOk());
    }

    // --- Sem autenticacao -------------------------------------------------

    @Test
    void anonymousStillSeesThePublicDashboard() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/api/metrics/public")).andExpect(status().isOk());
    }

    @Test
    void anonymousIsSentToTheLoginPage() throws Exception {
        mockMvc.perform(get("/containers")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/users")).andExpect(status().is3xxRedirection());
    }
}
