package com.homeServer.server_dashboard.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * O login precisa funcionar quando a senha e' fornecida apenas como hash
 * (DASHBOARD_ADMIN_PASSWORD_HASH), sem nenhuma senha em texto puro no ambiente. O hash usado aqui
 * vem sem o prefixo {@code {bcrypt}}, como o de ferramentas externas (htpasswd, libs bcrypt).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminPasswordHashIntegrationTest {

    private static final String PASSWORD = "senha-do-hash";

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void adminCredentials(DynamicPropertyRegistry registry) {
        registry.add("dashboard.admin.password", () -> "");
        registry.add("dashboard.admin.password-hash", () -> new BCryptPasswordEncoder().encode(PASSWORD));
    }

    @Test
    void acceptsTheCorrectPasswordAgainstTheConfiguredHash() throws Exception {
        login(PASSWORD).andExpect(redirectedUrl("/"));
    }

    @Test
    void rejectsAWrongPassword() throws Exception {
        login("senha-errada").andExpect(redirectedUrl("/login?error"));
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/login")
                .param("username", "admin")
                .param("password", password)
                .with(csrf()));
    }
}
