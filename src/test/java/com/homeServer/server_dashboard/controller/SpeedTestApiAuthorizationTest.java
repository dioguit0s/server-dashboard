package com.homeServer.server_dashboard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homeServer.server_dashboard.service.SpeedTestService;
import com.homeServer.server_dashboard.service.SpeedTestService.SpeedTestStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Ler o resultado e' de qualquer VIEWER; disparar um teste (que satura o link) e' so' de ADMIN. */
@SpringBootTest
@AutoConfigureMockMvc
class SpeedTestApiAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpeedTestService speedTestService;

    private static SpeedTestStatus idle() {
        return new SpeedTestStatus(false, null, null, null, null, null, List.of(), 0);
    }

    @Test
    void viewerReadsTheLastResult() throws Exception {
        when(speedTestService.getStatus()).thenReturn(idle());
        mockMvc.perform(get("/api/speedtest").with(user("observador").roles("VIEWER")))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousCannotReadTheResult() throws Exception {
        mockMvc.perform(get("/api/speedtest")).andExpect(status().is3xxRedirection());
    }

    @Test
    void viewerCannotStartATest() throws Exception {
        mockMvc.perform(post("/api/speedtest/run").with(user("observador").roles("VIEWER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminStartsATest() throws Exception {
        when(speedTestService.startAsync("manual")).thenReturn(true);
        when(speedTestService.getStatus()).thenReturn(idle());
        mockMvc.perform(post("/api/speedtest/run").with(user("chefe").roles("ADMIN", "VIEWER")).with(csrf()))
                .andExpect(status().isAccepted());
    }

    @Test
    void secondTestWhileRunningIsAConflict() throws Exception {
        when(speedTestService.startAsync("manual")).thenReturn(false);
        when(speedTestService.getStatus()).thenReturn(idle());
        mockMvc.perform(post("/api/speedtest/run").with(user("chefe").roles("ADMIN", "VIEWER")).with(csrf()))
                .andExpect(status().isConflict());
    }
}
