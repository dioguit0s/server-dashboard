package com.homeServer.server_dashboard.config;

import org.apache.catalina.valves.RemoteIpValve;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resolve o IP do visitante atras do Cloudflare Tunnel — a base do rate limiting do login.
 *
 * <p>O {@code X-Forwarded-For} nao serve: a Cloudflare <em>acrescenta</em> ao valor que o cliente
 * mandou, e o primeiro item (o que o Spring usaria) fica sob controle do atacante, que trocaria de
 * "IP" a cada tentativa e escaparia do bloqueio. O {@code CF-Connecting-IP} e' preenchido pela
 * Cloudflare com um unico IP e sobrescreve o que vier do cliente.
 *
 * <p>A valvula so' confia no header quando a conexao vem de um proxy listado em
 * {@code trusted-proxies} — por padrao, so' o loopback (cloudflared no mesmo host). Acesso direto
 * pela LAN ignora o header e usa o IP real da conexao. Tambem aplica o {@code X-Forwarded-Proto},
 * para que redirects saiam com {@code https://}.
 *
 * <p>Configurado aqui, e nao via {@code server.tomcat.remoteip.*}, para que os testes exercitem a
 * mesma fiacao de producao (o application.properties de teste substitui o principal por inteiro).
 */
@Configuration
public class ClientIpConfig {

    /** Regex do Tomcat: IPv4 e IPv6 de loopback, como o Tomcat os reporta. */
    static final String LOOPBACK_PROXIES = "127\\.0\\.0\\.1|0:0:0:0:0:0:0:1";

    @Bean
    WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory> clientIpValveCustomizer(
            @Value("${dashboard.client-ip.header:CF-Connecting-IP}") String clientIpHeader,
            @Value("${dashboard.client-ip.trusted-proxies:}") String trustedProxies) {
        String internalProxies = trustedProxies.isBlank() ? LOOPBACK_PROXIES : trustedProxies;
        return factory -> {
            RemoteIpValve valve = new RemoteIpValve();
            valve.setRemoteIpHeader(clientIpHeader);
            valve.setProtocolHeader("X-Forwarded-Proto");
            valve.setInternalProxies(internalProxies);
            factory.addEngineValves(valve);
        };
    }
}
