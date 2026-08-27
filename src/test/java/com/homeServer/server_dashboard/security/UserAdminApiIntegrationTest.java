package com.homeServer.server_dashboard.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.homeServer.server_dashboard.model.DashboardRole;
import com.homeServer.server_dashboard.model.DashboardUser;
import com.homeServer.server_dashboard.repository.DashboardUserRepository;

/**
 * Gestao de usuarios: quem pode chamar, e as travas que impedem o dashboard de ficar sem
 * administrador.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserAdminApiIntegrationTest {

    private static final String ADMIN_USERNAME = "admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DashboardUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void removeUsersCreatedByTheScenarios() {
        userRepository.findAllByOrderByUsernameAsc().stream()
                .filter(user -> !ADMIN_USERNAME.equals(user.getUsername()))
                .forEach(userRepository::delete);
    }

    private static UserRequestPostProcessor actingAdmin() {
        return user(ADMIN_USERNAME).roles("ADMIN", "VIEWER");
    }

    @Test
    void createsAViewerAndStoresThePasswordHashed() throws Exception {
        createUser("observador", "senha-de-teste", "\"VIEWER\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("observador"))
                .andExpect(jsonPath("$.roles[0]").value("VIEWER"))
                .andExpect(jsonPath("$.enabled").value(true));

        DashboardUser created = userRepository.findByUsernameIgnoreCase("observador").orElseThrow();
        assertEquals("senha-de-teste".equals(created.getPasswordHash()), false);
        assertTrue(passwordEncoder.matches("senha-de-teste", created.getPasswordHash()));
    }

    @Test
    void neverExposesSecretsInTheListing() throws Exception {
        createUser("observador", "senha-de-teste", "\"VIEWER\"").andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/users").with(actingAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[*].totpSecret").doesNotExist())
                .andExpect(jsonPath("$[*].recoveryCodes").doesNotExist());
    }

    @Test
    void normalizesTheUsernameAndRejectsDuplicates() throws Exception {
        createUser("Observador", "senha-de-teste", "\"VIEWER\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("observador"));

        createUser("OBSERVADOR", "outra-senha-boa", "\"VIEWER\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Ja existe um usuario com esse nome"));
    }

    @Test
    void rejectsAShortPassword() throws Exception {
        createUser("observador", "curta", "\"VIEWER\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("8 caracteres")));
    }

    @Test
    void rejectsAnUnknownRole() throws Exception {
        createUser("observador", "senha-de-teste", "\"SUPERUSER\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Papel desconhecido")));
    }

    /**
     * Cenario completo da trava: dois admins, um deles e' desabilitado (o que e' permitido, porque
     * ainda sobra outro), e a remocao do que restou passa a ser recusada.
     */
    @Test
    void refusesToRemoveTheOnlyActiveAdmin() throws Exception {
        createUser("outro.admin", "senha-de-teste", "\"VIEWER\",\"ADMIN\"").andExpect(status().isOk());
        Long otherAdminIdentifier = userRepository.findByUsernameIgnoreCase("outro.admin").orElseThrow().getIdentifier();
        Long adminIdentifier = userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow().getIdentifier();

        mockMvc.perform(put("/api/admin/users/" + otherAdminIdentifier + "/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}")
                        .with(actingAdmin()).with(csrf()))
                .andExpect(status().isOk());

        // Agora 'admin' e' o unico admin ativo; quem remove e' o outro admin, entao a trava que
        // dispara e' a do ultimo administrador, e nao a da propria conta.
        mockMvc.perform(delete("/api/admin/users/" + adminIdentifier)
                        .with(user("outro.admin").roles("ADMIN", "VIEWER")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("unico administrador")));

        assertTrue(userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).isPresent());
    }

    @Test
    void refusesToDemoteTheOnlyActiveAdmin() throws Exception {
        Long adminIdentifier = userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow().getIdentifier();

        mockMvc.perform(put("/api/admin/users/" + adminIdentifier + "/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"VIEWER\"]}")
                        .with(actingAdmin()).with(csrf()))
                .andExpect(status().isBadRequest());

        DashboardUser admin = userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow();
        assertTrue(admin.hasRole(DashboardRole.ADMIN));
    }

    @Test
    void allowsDemotingAnAdminOnceAnotherOneExists() throws Exception {
        createUser("segundo.admin", "senha-de-teste", "\"VIEWER\",\"ADMIN\"").andExpect(status().isOk());
        Long adminIdentifier = userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow().getIdentifier();

        mockMvc.perform(put("/api/admin/users/" + adminIdentifier + "/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"VIEWER\"]}")
                        .with(actingAdmin()).with(csrf()))
                .andExpect(status().isOk());

        // Restaura o admin para nao afetar os cenarios seguintes, que compartilham o contexto.
        DashboardUser admin = userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow();
        admin.setRoles(java.util.EnumSet.of(DashboardRole.ADMIN));
        userRepository.save(admin);
    }

    /**
     * Desabilitar ou remover a propria conta e' o jeito mais facil de se trancar para fora, entao e'
     * recusado mesmo quando existem outros admins.
     */
    @Test
    void refusesToDisableOrRemoveTheAccountInUse() throws Exception {
        Long adminIdentifier = userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow().getIdentifier();

        mockMvc.perform(put("/api/admin/users/" + adminIdentifier + "/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}")
                        .with(actingAdmin()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("propria conta")));

        mockMvc.perform(delete("/api/admin/users/" + adminIdentifier).with(actingAdmin()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("propria conta")));
    }

    @Test
    void resetsAnotherUsersPassword() throws Exception {
        createUser("observador", "senha-de-teste", "\"VIEWER\"").andExpect(status().isOk());
        Long identifier = userRepository.findByUsernameIgnoreCase("observador").orElseThrow().getIdentifier();

        mockMvc.perform(put("/api/admin/users/" + identifier + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"nova-senha-boa\"}")
                        .with(actingAdmin()).with(csrf()))
                .andExpect(status().isOk());

        DashboardUser updated = userRepository.findByUsernameIgnoreCase("observador").orElseThrow();
        assertTrue(passwordEncoder.matches("nova-senha-boa", updated.getPasswordHash()));
    }

    @Test
    void viewerCannotUseAnyOfTheseEndpoints() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"intruso\",\"password\":\"senha-de-teste\",\"roles\":[\"ADMIN\"]}")
                        .with(user("observador").roles("VIEWER")).with(csrf()))
                .andExpect(status().isForbidden());

        assertTrue(userRepository.findByUsernameIgnoreCase("intruso").isEmpty());
    }

    private ResultActions createUser(String username, String password, String rolesJson) throws Exception {
        return mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"roles\":[" + rolesJson + "]}")
                .with(actingAdmin()).with(csrf()));
    }
}
