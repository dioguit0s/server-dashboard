package com.homeServer.server_dashboard.config;

import java.time.Clock;

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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;

import com.homeServer.server_dashboard.security.AdminCredentials;
import com.homeServer.server_dashboard.security.LoginAttemptFilter;
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   LoginAttemptFilter loginAttemptFilter,
                                                   TwoFactorEnrollmentFilter twoFactorEnrollmentFilter,
                                                   AuthenticationProvider authenticationProvider,
                                                   AuthenticationFailureHandler authenticationFailureHandler) throws Exception {
        http
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(loginAttemptFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(twoFactorEnrollmentFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/charts", "/cpu-details", "/disk-details", "/ram-details", "/login", "/login/2fa", "/home/**", "/css/**", "/js/**", "/ws/**", "/api/metrics/public", "/api/metrics/history", "/favicon.ico", "/error", "/manifest.json", "/sw.js", "/icons/**").permitAll()
                // Escrita: so' ADMIN. Precisa vir antes da regra de leitura, que casa os mesmos caminhos.
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/docker/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/services/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/services/**").hasRole("ADMIN")
                // A propria conta (troca de senha e 2FA) e' de quem esta logado, qualquer que seja o
                // papel. Precisa vir antes de /api/**, que casaria /api/account/** primeiro.
                .requestMatchers("/account/**", "/api/account/**").authenticated()
                // Leitura de dados sensiveis: VIEWER ja basta.
                .requestMatchers("/processes", "/services", "/containers", "/logs", "/api/**").hasAnyRole("VIEWER", "ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureHandler(authenticationFailureHandler)
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
            )
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
    public SpringSecurityDialect springSecurityDialect() {
        return new SpringSecurityDialect();
    }
}
