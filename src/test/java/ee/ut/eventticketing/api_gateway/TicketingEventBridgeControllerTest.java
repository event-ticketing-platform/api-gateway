package ee.ut.eventticketing.api_gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;

import ee.ut.eventticketing.api_gateway.security.JwtProperties;

class TicketingEventBridgeControllerTest {

    @Test
    void createTicketTypeForwardsAuthorizationToTicketingService() throws IOException {
        try (StubServer ticketingServer = new StubServer("/events/00000000-0000-0000-0000-000000000001/tickettypes")) {
            WebTestClient client = bindClient("http://127.0.0.1:1", ticketingServer.baseUrl());

            client.post()
                    .uri("/api/events/1/tickettypes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                    .bodyValue(Map.of("name", "VIP"))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.ok").isEqualTo(true);

            assertThat(ticketingServer.authorization()).isEqualTo("Bearer user-token");
        }
    }

    @Test
    void adapterForwardsAuthorizationToEventService() throws IOException {
        try (StubServer eventServer = new StubServer("/events/1")) {
            WebTestClient client = bindClient(eventServer.baseUrl(), "http://127.0.0.1:1");

            client.get()
                    .uri("/ticketing-event-adapter/events/00000000-0000-0000-0000-000000000001")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.ok").isEqualTo(true);

            assertThat(eventServer.authorization()).isEqualTo("Bearer user-token");
        }
    }

    @Test
    void adapterUsesServiceTokenWhenTicketingServiceDoesNotForwardAuthorization() throws IOException {
        try (StubServer eventServer = new StubServer("/events/1")) {
            WebTestClient client = bindClient(eventServer.baseUrl(), "http://127.0.0.1:1");

            client.get()
                    .uri("/ticketing-event-adapter/events/00000000-0000-0000-0000-000000000001")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.ok").isEqualTo(true);

            assertThat(eventServer.authorization()).isEqualTo("Bearer service-token");
        }
    }

    @Test
    void adapterPropagatesUpstreamUnauthorizedInsteadOfThrowingGatewayError() throws IOException {
        try (StubServer eventServer = new StubServer("/events/1", 401, "{\"error\":\"unauthorized\"}")) {
            WebTestClient client = bindClient(eventServer.baseUrl(), "http://127.0.0.1:1");

            client.get()
                    .uri("/ticketing-event-adapter/events/00000000-0000-0000-0000-000000000001")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.error").isEqualTo("unauthorized");
        }
    }

    private WebTestClient bindClient(String eventBaseUrl, String ticketingBaseUrl) {
        TicketingEventBridgeController controller = new TicketingEventBridgeController(
                WebClient.builder(),
                eventBaseUrl,
                ticketingBaseUrl,
                serviceTokenEncoder(),
                new JwtProperties("event-ticketing-gateway", "test-secret", 120));
        return WebTestClient.bindToController(controller).build();
    }

    private JwtEncoder serviceTokenEncoder() {
        return parameters -> Jwt.withTokenValue("service-token")
                .header("alg", "HS256")
                .claim("sub", "api-gateway-ticketing-adapter")
                .build();
    }

    private static final class StubServer implements AutoCloseable {
        private final HttpServer server;
        private volatile String authorization;

        StubServer(String path) throws IOException {
            this(path, 200, "{\"ok\":true}");
        }

        StubServer(String path, int status, String body) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(path, exchange -> respond(exchange, status, body));
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        String authorization() {
            return authorization;
        }

        private void respond(HttpExchange exchange, int status, String body) throws IOException {
            authorization = exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
