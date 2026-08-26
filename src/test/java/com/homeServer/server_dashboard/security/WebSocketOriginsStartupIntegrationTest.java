package com.homeServer.server_dashboard.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.homeServer.server_dashboard.ServerDashboardApplication;

/**
 * O guarda de origem so' cumpre o papel se derrubar a aplicacao de verdade: os testes unitarios
 * cobrem a deteccao do curinga, e estes cobrem a fiacao — que a aplicacao nao sobe com o curinga
 * fora de desenvolvimento, e sobe (com WARN) quando o perfil dev esta ativo.
 */
class WebSocketOriginsStartupIntegrationTest {

    /**
     * Como argumentos de linha de comando, e nao via {@code properties()}: essas entrariam como
     * default properties, abaixo do application.properties de teste, e o curinga nem chegaria a ser
     * avaliado.
     */
    private static final String WILDCARD_ORIGIN = "--dashboard.websocket.allowed-origin-patterns=*";
    private static final String RANDOM_PORT = "--server.port=0";

    @Test
    void doesNotStartWithTheWildcardOutsideDevelopment() {
        assertThatThrownBy(() -> run(application()))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DASHBOARD_WS_ORIGINS");
    }

    @Test
    void startsWithTheWildcardUnderTheDevelopmentProfile() {
        assertThatCode(() -> run(application().profiles("dev"))).doesNotThrowAnyException();
    }

    @Test
    void startsWithTheWildcardWhenItIsExplicitlyAllowed() {
        assertThatCode(() -> run(application(), "--dashboard.websocket.allow-wildcard-origins=true"))
                .doesNotThrowAnyException();
    }

    private static SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(ServerDashboardApplication.class);
    }

    private static void run(SpringApplicationBuilder builder, String... extraArguments) {
        String[] arguments = Stream.concat(Stream.of(WILDCARD_ORIGIN, RANDOM_PORT), Stream.of(extraArguments))
                .toArray(String[]::new);
        try (ConfigurableApplicationContext context = builder.run(arguments)) {
            // Subiu: o fechamento no try-with-resources devolve a porta e para os schedulers.
        }
    }
}
