package ee.ut.eventticketing.api_gateway;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
class GatewaySecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void loginReturnsJwtForStoredAdmin() {
        webTestClient.post()
                .uri("/api/auth/login")
                .bodyValue(Map.of("username", "admin", "password", "admin123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").exists()
                .jsonPath("$.accessToken").exists()
                .jsonPath("$.username").isEqualTo("admin");
    }

    @Test
    void registerCreatesCustomerAccountAndReturnsJwt() {
        String username = "user-" + UUID.randomUUID();
        webTestClient.post()
                .uri("/api/auth/register")
                .bodyValue(Map.of(
                        "username", username,
                        "email", username + "@example.com",
                        "password", "customer123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").exists()
                .jsonPath("$.accessToken").exists()
                .jsonPath("$.username").isEqualTo(username)
                .jsonPath("$.roles[0]").isEqualTo("CUSTOMER");
    }

    @Test
    void protectedApiRequiresAuthentication() {
        webTestClient.get()
                .uri("/api/bookings/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
