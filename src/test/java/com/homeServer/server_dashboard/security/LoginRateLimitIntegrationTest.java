package com.homeServer.server_dashboard.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "dashboard.security.login.max-attempts=3",
        "dashboard.security.login.lockout-minutes=15",
        "dashboard.security.login.global-max-attempts=0"
})
@AutoConfigureMockMvc
class LoginRateLimitIntegrationTest {

    private static final String USERNAME = "admin";
    private static final String CORRECT_PASSWORD = "test-password";
    private static final String WRONG_PASSWORD = "senha-errada";
    private static final int MAX_ATTEMPTS = 3;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void resetCounters() {
        // O bean e' compartilhado pelo contexto de teste; zera o estado entre os cenarios.
        loginAttemptService.recordSuccess("127.0.0.1");
    }

    @Test
    void rejectsFurtherAttemptsAfterTheConfiguredNumberOfFailures() throws Exception {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            login(WRONG_PASSWORD)
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?error"));
        }

        login(WRONG_PASSWORD)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?blocked"));
    }

    @Test
    void rejectsEvenTheCorrectPasswordWhileBlocked() throws Exception {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            login(WRONG_PASSWORD).andExpect(redirectedUrl("/login?error"));
        }

        login(CORRECT_PASSWORD)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?blocked"));
    }

    @Test
    void successfulLoginResetsTheCounterForThatAddress() throws Exception {
        for (int attempt = 0; attempt < MAX_ATTEMPTS - 1; attempt++) {
            login(WRONG_PASSWORD).andExpect(redirectedUrl("/login?error"));
        }

        login(CORRECT_PASSWORD).andExpect(redirectedUrl("/"));

        // Contador zerado: as proximas falhas voltam a ser tratadas como as primeiras.
        for (int attempt = 0; attempt < MAX_ATTEMPTS - 1; attempt++) {
            login(WRONG_PASSWORD).andExpect(redirectedUrl("/login?error"));
        }
    }

    private org.springframework.test.web.servlet.ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/login")
                .param("username", USERNAME)
                .param("password", password)
                .with(csrf()));
    }
}
