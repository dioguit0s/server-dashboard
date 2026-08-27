package com.homeServer.server_dashboard.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.homeServer.server_dashboard.model.DashboardRole;
import com.homeServer.server_dashboard.model.DashboardUser;
import com.homeServer.server_dashboard.repository.DashboardUserRepository;
import com.homeServer.server_dashboard.security.totp.Base32;
import com.homeServer.server_dashboard.security.totp.TotpGenerator;

/**
 * A area da propria conta: troca de senha e enrollment de 2FA.
 *
 * <p>Cada operacao que reduz a protecao da conta — trocar a senha, desligar o 2FA, reemitir codigos
 * de recuperacao — exige a senha atual, para que uma sessao sequestrada nao consiga se apropriar da
 * conta em definitivo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountApiIntegrationTest {

    private static final String USERNAME = "operador.conta";
    private static final String PASSWORD = "senha-do-operador";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DashboardUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createUser() {
        removeUser();
        userRepository.save(new DashboardUser(
                USERNAME, passwordEncoder.encode(PASSWORD), EnumSet.of(DashboardRole.VIEWER)));
    }

    @AfterEach
    void removeUser() {
        userRepository.findByUsernameIgnoreCase(USERNAME).ifPresent(userRepository::delete);
    }

    // --- Senha ------------------------------------------------------------

    @Test
    void changesTheOwnPassword() throws Exception {
        changePassword(PASSWORD, "nova-senha-boa").andExpect(status().isOk());

        assertTrue(passwordEncoder.matches("nova-senha-boa", reload().getPasswordHash()));
    }

    @Test
    void refusesToChangeThePasswordWithoutTheCurrentOne() throws Exception {
        changePassword("senha-errada", "nova-senha-boa")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Senha atual incorreta"));

        assertTrue(passwordEncoder.matches(PASSWORD, reload().getPasswordHash()));
    }

    @Test
    void refusesAShortNewPassword() throws Exception {
        changePassword(PASSWORD, "curta").andExpect(status().isBadRequest());

        assertTrue(passwordEncoder.matches(PASSWORD, reload().getPasswordHash()));
    }

    // --- Enrollment de 2FA -------------------------------------------------

    @Test
    void enrollsAndOnlyThenTurnsTwoFactorOn() throws Exception {
        String secret = beginEnrollment();

        // O segredo ja esta gravado, mas o 2FA ainda nao vale — quem fecha a pagina no meio nao
        // fica trancado para fora.
        assertFalse(reload().isTotpEnabled());

        confirmEnrollment(currentCode(secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10));

        DashboardUser enrolled = reload();
        assertTrue(enrolled.isTotpEnabled());
        assertTrue(enrolled.unusedRecoveryCodeCount() == 10);
    }

    @Test
    void refusesToTurnTwoFactorOnWithAWrongCode() throws Exception {
        beginEnrollment();

        confirmEnrollment("000000").andExpect(status().isBadRequest());

        assertFalse(reload().isTotpEnabled());
    }

    @Test
    void refusesToConfirmWithoutHavingStartedTheEnrollment() throws Exception {
        confirmEnrollment("000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Nenhum enrollment")));
    }

    // --- Desligar / reemitir ----------------------------------------------

    @Test
    void refusesToDisableTwoFactorWithoutTheCurrentPassword() throws Exception {
        confirmEnrollment(currentCode(beginEnrollment())).andExpect(status().isOk());

        mockMvc.perform(delete("/api/account/2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"senha-errada\"}")
                        .with(user(USERNAME).roles("VIEWER")).with(csrf()))
                .andExpect(status().isBadRequest());

        assertTrue(reload().isTotpEnabled());
    }

    @Test
    void disablesTwoFactorAndForgetsEverythingItDependedOn() throws Exception {
        confirmEnrollment(currentCode(beginEnrollment())).andExpect(status().isOk());

        mockMvc.perform(delete("/api/account/2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\"}")
                        .with(user(USERNAME).roles("VIEWER")).with(csrf()))
                .andExpect(status().isOk());

        DashboardUser disabled = reload();
        assertFalse(disabled.isTotpEnabled());
        assertTrue(disabled.getTotpSecret() == null);
        assertTrue(disabled.unusedRecoveryCodeCount() == 0);
    }

    @Test
    void reissuesRecoveryCodesOnlyWithTheCurrentPassword() throws Exception {
        confirmEnrollment(currentCode(beginEnrollment())).andExpect(status().isOk());

        mockMvc.perform(post("/api/account/2fa/recovery-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"senha-errada\"}")
                        .with(user(USERNAME).roles("VIEWER")).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/account/2fa/recovery-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\"}")
                        .with(user(USERNAME).roles("VIEWER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10));
    }

    @Test
    void anonymousCannotTouchAnyAccountEndpoint() throws Exception {
        mockMvc.perform(post("/api/account/2fa/enrollment").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    // --- Auxiliares --------------------------------------------------------

    private String beginEnrollment() throws Exception {
        mockMvc.perform(post("/api/account/2fa/enrollment").with(user(USERNAME).roles("VIEWER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrCode").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));
        return reload().getTotpSecret();
    }

    private org.springframework.test.web.servlet.ResultActions confirmEnrollment(String code) throws Exception {
        return mockMvc.perform(post("/api/account/2fa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}")
                .with(user(USERNAME).roles("VIEWER")).with(csrf()));
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(String current, String updated) throws Exception {
        return mockMvc.perform(put("/api/account/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + updated + "\"}")
                .with(user(USERNAME).roles("VIEWER")).with(csrf()));
    }

    private static String currentCode(String secret) {
        return TotpGenerator.generate(
                Base32.decode(secret), TotpGenerator.timeStepAt(java.time.Instant.now().getEpochSecond()));
    }

    private DashboardUser reload() {
        return userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();
    }
}
