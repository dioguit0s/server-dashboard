package com.homeServer.server_dashboard.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.homeServer.server_dashboard.model.DashboardRole;
import com.homeServer.server_dashboard.model.DashboardUser;
import com.homeServer.server_dashboard.repository.DashboardUserRepository;
import com.homeServer.server_dashboard.security.totp.Base32;
import com.homeServer.server_dashboard.security.totp.TotpGenerator;
import com.homeServer.server_dashboard.service.TwoFactorService;

/**
 * Login em dois passos de ponta a ponta.
 *
 * <p>A garantia mais importante verificada aqui e' negativa: acertar a senha de uma conta com 2FA
 * <b>nao</b> autentica ninguem. So' o segundo fator conclui o login.
 */
@SpringBootTest(properties = {
        // Sem lockout atrapalhando os cenarios que erram o codigo de proposito.
        "dashboard.security.login.max-attempts=50",
        "dashboard.security.login.global-max-attempts=0"
})
@AutoConfigureMockMvc
class TwoFactorLoginIntegrationTest {

    private static final String USERNAME = "operador.2fa";
    private static final String PASSWORD = "senha-do-operador";
    private static final String REMEMBER_ME_COOKIE = "remember-me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DashboardUserRepository userRepository;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    private String secret;
    private List<String> recoveryCodes;

    @BeforeEach
    void createUserWithTwoFactorEnabled() {
        removeTestUser();
        loginAttemptService.recordSuccess("127.0.0.1");

        DashboardUser user = new DashboardUser(
                USERNAME, passwordEncoder.encode(PASSWORD), EnumSet.of(DashboardRole.VIEWER));
        userRepository.save(user);

        secret = twoFactorService.beginEnrollment(USERNAME).secret();
        recoveryCodes = twoFactorService.confirmEnrollment(USERNAME, currentCode());
    }

    @AfterEach
    void removeTestUser() {
        userRepository.findByUsernameIgnoreCase(USERNAME).ifPresent(userRepository::delete);
    }

    // --- Passo 1: a senha sozinha nao autentica ---------------------------

    @Test
    void correctPasswordAloneDoesNotAuthenticate() throws Exception {
        submitPassword(PASSWORD)
                .andExpect(redirectedUrl("/login/2fa"))
                .andExpect(unauthenticated());
    }

    @Test
    void wrongPasswordNeverReachesTheSecondStep() throws Exception {
        submitPassword("senha-errada")
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void theSecondStepPageIsUnreachableWithoutHavingPassedThePassword() throws Exception {
        mockMvc.perform(get("/login/2fa"))
                .andExpect(redirectedUrl("/login"));
    }

    /**
     * Sem sessao pendente, postar um codigo — mesmo o correto — nao pode autenticar ninguem.
     */
    @Test
    void submittingACodeWithoutThePasswordStepAuthenticatesNobody() throws Exception {
        mockMvc.perform(post("/login/2fa").param("code", currentCode()).with(csrf()))
                .andExpect(redirectedUrl("/login?expired"))
                .andExpect(unauthenticated());
    }

    // --- Passo 2: o segundo fator conclui ---------------------------------

    @Test
    void validCodeCompletesTheLogin() throws Exception {
        MockHttpSession session = passwordStep();

        mockMvc.perform(post("/login/2fa").session(session).param("code", currentCode()).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(USERNAME).withRoles("VIEWER"));
    }

    @Test
    void wrongCodeKeepsTheUserOut() throws Exception {
        MockHttpSession session = passwordStep();

        mockMvc.perform(post("/login/2fa").session(session).param("code", "000000").with(csrf()))
                .andExpect(redirectedUrl("/login/2fa?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void theSameCodeCannotBeUsedTwice() throws Exception {
        String code = currentCode();

        mockMvc.perform(post("/login/2fa").session(passwordStep()).param("code", code).with(csrf()))
                .andExpect(authenticated());

        // Segunda sessao, mesmo codigo ainda dentro da janela de validade: o anti-replay recusa.
        mockMvc.perform(post("/login/2fa").session(passwordStep()).param("code", code).with(csrf()))
                .andExpect(redirectedUrl("/login/2fa?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void aRecoveryCodeWorksOnceAndOnlyOnce() throws Exception {
        String recoveryCode = recoveryCodes.get(0);

        mockMvc.perform(post("/login/2fa").session(passwordStep()).param("code", recoveryCode).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(USERNAME));

        mockMvc.perform(post("/login/2fa").session(passwordStep()).param("code", recoveryCode).with(csrf()))
                .andExpect(redirectedUrl("/login/2fa?error"))
                .andExpect(unauthenticated());
    }

    /**
     * O passo da senha nao pode contar como login bem-sucedido: se contasse, o contador de
     * tentativas do IP seria zerado e quem ja tivesse a senha teria codigos TOTP infinitos para
     * tentar, sem nunca ser bloqueado.
     */
    @Test
    void passingThePasswordStepDoesNotResetTheRateLimitCounter() throws Exception {
        // Duas falhas de senha acumuladas...
        submitPassword("senha-errada").andExpect(redirectedUrl("/login?error"));
        submitPassword("senha-errada").andExpect(redirectedUrl("/login?error"));

        // ...e agora a senha correta, que apenas leva ao segundo fator.
        submitPassword(PASSWORD).andExpect(redirectedUrl("/login/2fa"));

        // Como o login nao se concluiu, as falhas anteriores continuam valendo: o bloqueio chega
        // com as tentativas restantes, e nao 50 tentativas depois.
        MockHttpSession session = passwordStep();
        for (int attempt = 0; attempt < 48; attempt++) {
            mockMvc.perform(post("/login/2fa").session(session).param("code", "000000").with(csrf()));
        }
        assertTrue(loginAttemptService.isBlocked("127.0.0.1"));
        loginAttemptService.recordSuccess("127.0.0.1");
    }

    @Test
    void aFailedCodeCountsTowardsTheRateLimitOfTheAddress() throws Exception {
        assertFalse(loginAttemptService.isBlocked("127.0.0.1"));

        MockHttpSession session = passwordStep();
        for (int attempt = 0; attempt < 50; attempt++) {
            mockMvc.perform(post("/login/2fa").session(session).param("code", "000000").with(csrf()));
        }

        // Sem contabilizar as falhas do segundo passo, quem ja tem a senha teria tentativas infinitas.
        assertTrue(loginAttemptService.isBlocked("127.0.0.1"));
        loginAttemptService.recordSuccess("127.0.0.1");
    }

    // --- Conta sem 2FA continua entrando em um passo ----------------------

    @Test
    void accountsWithoutTwoFactorStillLogInWithThePasswordAlone() throws Exception {
        mockMvc.perform(post("/login").param("username", "admin").param("password", "test-password").with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("admin"));
    }

    // --- "Lembrar de mim" ---------------------------------------------------

    @Test
    void rememberMeCookieIsNotSetWithoutTheCheckbox() throws Exception {
        var result = mockMvc.perform(post("/login")
                        .param("username", "admin").param("password", "test-password").with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andReturn();

        assertThat(result.getResponse().getCookie(REMEMBER_ME_COOKIE)).isNull();
    }

    @Test
    void rememberMeCookieIsSetWhenTheCheckboxIsChecked() throws Exception {
        var result = mockMvc.perform(post("/login")
                        .param("username", "admin").param("password", "test-password")
                        .param("remember-me", "on").with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andReturn();

        assertThat(result.getResponse().getCookie(REMEMBER_ME_COOKIE)).isNotNull();
    }

    /**
     * O cookie so' pode nascer depois que o segundo fator for aceito — nunca so' com a senha, senao
     * o 2FA perderia o sentido em qualquer acesso futuro pelo cookie.
     */
    @Test
    void rememberMeCookieIsOnlySetAfterTheSecondFactorSucceeds() throws Exception {
        var passwordResult = mockMvc.perform(post("/login")
                        .param("username", USERNAME).param("password", PASSWORD)
                        .param("remember-me", "on").with(csrf()))
                .andExpect(redirectedUrl("/login/2fa"))
                .andReturn();

        // Uma senha certa que leva ao segundo fator conta como falha de autenticacao para o Spring
        // Security (nenhum Authentication completo foi produzido), entao o RememberMeServices reage
        // como reage a qualquer login mal-sucedido: cancela um eventual cookie antigo do navegador
        // (valor vazio, expira na hora) — nao emite um token de verdade.
        assertThat(hasNoActiveRememberMeToken(passwordResult.getResponse())).isTrue();

        MockHttpSession session = (MockHttpSession) passwordResult.getRequest().getSession(false);
        var secondFactorResult = mockMvc.perform(post("/login/2fa")
                        .session(session).param("code", currentCode()).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andReturn();

        assertThat(hasNoActiveRememberMeToken(secondFactorResult.getResponse())).isFalse();
    }

    private static boolean hasNoActiveRememberMeToken(org.springframework.mock.web.MockHttpServletResponse response) {
        var cookie = response.getCookie(REMEMBER_ME_COOKIE);
        return cookie == null || cookie.getValue() == null || cookie.getValue().isEmpty();
    }

    /**
     * Executa o passo da senha e devolve a sessao ja com o estado pendente de 2FA.
     */
    private MockHttpSession passwordStep() throws Exception {
        return (MockHttpSession) submitPassword(PASSWORD)
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    private ResultActions submitPassword(String password) throws Exception {
        return mockMvc.perform(post("/login")
                .param("username", USERNAME)
                .param("password", password)
                .with(csrf()));
    }

    private String currentCode() {
        return TotpGenerator.generate(
                Base32.decode(secret), TotpGenerator.timeStepAt(java.time.Instant.now().getEpochSecond()));
    }
}
