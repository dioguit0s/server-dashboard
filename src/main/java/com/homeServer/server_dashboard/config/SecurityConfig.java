package com.homeServer.server_dashboard.config;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;

import com.homeServer.server_dashboard.repository.PersistentLoginTokenRepository;
import com.homeServer.server_dashboard.security.AdminCredentials;
import com.homeServer.server_dashboard.security.JpaPersistentTokenRepository;
import com.homeServer.server_dashboard.security.LoginAttemptFilter;
import com.homeServer.server_dashboard.security.RevokingPersistentTokenBasedRememberMeServices;
import com.homeServer.server_dashboard.security.TwoFactorAuthenticationFailureHandler;
import com.homeServer.server_dashboard.security.TwoFactorAuthenticationProvider;
import com.homeServer.server_dashboard.security.TwoFactorEnrollmentFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String DEFAULT_LOGIN_FAILURE_URL = "/login?error";

    @Value("${dashboard.admin.username}")
    private String adminUsername;

    @Value("${dashboard.admin.password:}")
    private String adminPassword;

    @Value("${dashboard.admin.password-hash:}")
    private String adminPasswordHash;

    @Value("${dashboard.security.remember-me.key:}")
    private String rememberMeKey;

    @Value("${dashboard.security.remember-me.validity-days:30}")
    private int rememberMeValidityDays;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   LoginAttemptFilter loginAttemptFilter,
                                                   TwoFactorEnrollmentFilter twoFactorEnrollmentFilter,
                                                   AuthenticationProvider authenticationProvider,
                                                   AuthenticationFailureHandler authenticationFailureHandler,
                                                   RememberMeServices rememberMeServices) throws Exception {
        http
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(loginAttemptFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(twoFactorEnrollmentFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // So' o essencial para chegar ate o login fica publico; nenhuma tela ou API de dados
                // do servidor sobra fora de autenticacao.
                .requestMatchers("/login", "/login/2fa", "/home/**", "/css/**", "/js/**", "/favicon.ico", "/error", "/manifest.json", "/sw.js", "/icons/**").permitAll()
                // Handshake do SockJS: e' so' a conexao HTTP inicial. Quem decide o que cada usuario
                // recebe e' a autorizacao STOMP (WebSocketSecurityBeans), nao esta linha.
                .requestMatchers("/ws/**").permitAll()
                // Escrita: so' ADMIN. Precisa vir antes da regra de leitura, que casa os mesmos caminhos.
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/docker/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/services/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/services/**").hasRole("ADMIN")
                // A propria conta (troca de senha e 2FA) e' de quem esta logado, qualquer que seja o
                // papel. Precisa vir antes de /api/**, que casaria /api/account/** primeiro.
                .requestMatchers("/account/**", "/api/account/**").authenticated()
                // Leitura de dados do servidor: VIEWER ja basta. O dashboard e suas paginas de detalhe
                // entram aqui tambem — deixaram de ser publicos.
                .requestMatchers("/", "/charts", "/cpu-details", "/disk-details", "/ram-details", "/processes", "/services", "/containers", "/logs", "/api/**").hasAnyRole("VIEWER", "ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureHandler(authenticationFailureHandler)
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login")
            )
            .rememberMe(rememberMe -> rememberMe.rememberMeServices(rememberMeServices))
            // withHttpOnlyFalse() e' intencional, nao um descuido: csrf-utils.js le o cookie
            // XSRF-TOKEN via JS para mandar o header em cada fetch. Trocar para HttpOnly=true
            // quebra esse fluxo. O trade-off (um XSS na aplicacao passaria a conseguir ler o
            // token) esta documentado no README; a mitigacao e' a disciplina de escape do
            // frontend, nao o HttpOnly do cookie CSRF.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/ws/**")
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
            );
        return http.build();
    }

    /**
     * O {@link LoginAttemptFilter} deve rodar apenas dentro da cadeia do Spring Security
     * (depois do CsrfFilter); esta registration desativa o auto-registro de beans Filter feito
     * pelo Boot, que o colocaria tambem na cadeia global do servlet container.
     */
    @Bean
    public FilterRegistrationBean<LoginAttemptFilter> loginAttemptFilterRegistration(LoginAttemptFilter loginAttemptFilter) {
        FilterRegistrationBean<LoginAttemptFilter> registration = new FilterRegistrationBean<>(loginAttemptFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Mesmo motivo do registro acima: o filtro de enrollment depende do {@code SecurityContext} ja
     * preenchido, entao so' faz sentido dentro da cadeia do Spring Security.
     */
    @Bean
    public FilterRegistrationBean<TwoFactorEnrollmentFilter> twoFactorEnrollmentFilterRegistration(
            TwoFactorEnrollmentFilter twoFactorEnrollmentFilter) {
        FilterRegistrationBean<TwoFactorEnrollmentFilter> registration =
                new FilterRegistrationBean<>(twoFactorEnrollmentFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * {@link DelegatingPasswordEncoder} para que o hash armazenado carregue o proprio algoritmo
     * ({@code {bcrypt}$2a$...}) e possa ser trocado no futuro sem mudar codigo. O encoder padrao
     * para {@code matches()} e' BCrypt, cobrindo hashes gerados por ferramentas externas, que vem
     * sem o prefixo.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder passwordEncoder =
            (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        passwordEncoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return passwordEncoder;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public AdminCredentials adminCredentials(PasswordEncoder passwordEncoder) {
        return new AdminCredentials(adminUsername, adminPassword, adminPasswordHash, passwordEncoder);
    }

    /**
     * Autenticacao por senha, envolvida pela checagem de 2FA: com o segundo fator ligado, o acerto
     * da senha ainda nao produz um {@code Authentication} completo.
     */
    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                         PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider passwordProvider = new DaoAuthenticationProvider(userDetailsService);
        passwordProvider.setPasswordEncoder(passwordEncoder);
        return new TwoFactorAuthenticationProvider(passwordProvider);
    }

    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler(Clock clock) {
        return new TwoFactorAuthenticationFailureHandler(DEFAULT_LOGIN_FAILURE_URL, clock);
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository(PersistentLoginTokenRepository repository) {
        return new JpaPersistentTokenRepository(repository);
    }

    /**
     * "Lembrar de mim": token persistente por serie, guardado no H2. Sem chave fixa configurada,
     * uma e' sorteada a cada boot — os cookies ja emitidos deixam de valer a cada restart, o que e'
     * aceitavel em dev mas deve ser evitado em producao definindo {@code DASHBOARD_REMEMBER_ME_KEY}.
     */
    @Bean
    public RememberMeServices rememberMeServices(UserDetailsService userDetailsService,
                                                 PersistentTokenRepository persistentTokenRepository,
                                                 PersistentLoginTokenRepository tokenRepository) {
        String key = (rememberMeKey == null || rememberMeKey.isBlank()) ? UUID.randomUUID().toString() : rememberMeKey;
        RevokingPersistentTokenBasedRememberMeServices services = new RevokingPersistentTokenBasedRememberMeServices(
                key, userDetailsService, persistentTokenRepository, tokenRepository);
        services.setTokenValiditySeconds((int) Duration.ofDays(rememberMeValidityDays).toSeconds());
        return services;
    }

    @Bean
    public SpringSecurityDialect springSecurityDialect() {
        return new SpringSecurityDialect();
    }
}
