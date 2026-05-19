package ee.ut.eventticketing.api_gateway;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import ee.ut.eventticketing.api_gateway.security.JwtProperties;
import reactor.core.publisher.Mono;

@RestController
public class TicketingEventBridgeController {

    private static final long UUID_NODE_MASK = 0x0000FFFFFFFFFFFFL;
    private static final long UUID_PREFIX_MASK = 0xFFFF000000000000L;

    private final WebClient eventClient;
    private final WebClient ticketingClient;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public TicketingEventBridgeController(
            WebClient.Builder webClientBuilder,
            @Value("${services.event.base-url}") String eventBaseUrl,
            @Value("${services.ticketing.base-url}") String ticketingBaseUrl,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties) {
        this.eventClient = webClientBuilder.clone().baseUrl(eventBaseUrl).build();
        this.ticketingClient = webClientBuilder.clone().baseUrl(ticketingBaseUrl).build();
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    @GetMapping({
            "/api/events/{eventId}/tickettypes",
            "/api/events/{eventId}/ticket-types",
            "/events/{eventId}/tickettypes",
            "/events/{eventId}/ticket-types"
    })
    public Mono<ResponseEntity<Object>> listTicketTypes(
            @PathVariable long eventId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UUID ticketingEventId = toTicketingEventId(eventId);
        return ticketingClient.get()
                .uri("/events/{eventId}/tickettypes", ticketingEventId)
                .headers(headers -> setAuthorization(headers, authorization))
                .exchangeToMono(response -> {
                    if (HttpStatus.NOT_FOUND.equals(response.statusCode())) {
                        return response.releaseBody().thenReturn(ResponseEntity.ok(List.of()));
                    }
                    return response.toEntity(Object.class);
                });
    }

    @PostMapping({
            "/api/events/{eventId}/tickettypes",
            "/api/events/{eventId}/ticket-types",
            "/events/{eventId}/tickettypes",
            "/events/{eventId}/ticket-types"
    })
    public Mono<ResponseEntity<Object>> createTicketType(
            @PathVariable long eventId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UUID ticketingEventId = toTicketingEventId(eventId);
        return ticketingClient.post()
                .uri("/events/{eventId}/tickettypes", ticketingEventId)
                .headers(headers -> setAuthorization(headers, authorization))
                .bodyValue(body)
                .exchangeToMono(response -> response.toEntity(Object.class));
    }

    @GetMapping("/ticketing-event-adapter/events/{ticketingEventId}")
    public Mono<ResponseEntity<Object>> getEventForTicketing(
            @PathVariable UUID ticketingEventId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        long eventId = fromTicketingEventId(ticketingEventId);
        return eventClient.get()
                .uri("/events/{eventId}", eventId)
                .headers(headers -> setAuthorization(headers, authorizationOrServiceToken(authorization)))
                .exchangeToMono(response -> response.toEntity(Object.class));
    }

    static UUID toTicketingEventId(long eventId) {
        if (eventId <= 0 || eventId > UUID_NODE_MASK) {
            throw new IllegalArgumentException("Event id is outside the bridgeable range: " + eventId);
        }
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012x", eventId));
    }

    static long fromTicketingEventId(UUID ticketingEventId) {
        if (ticketingEventId.getMostSignificantBits() != 0
                || (ticketingEventId.getLeastSignificantBits() & UUID_PREFIX_MASK) != 0) {
            throw new IllegalArgumentException("Ticketing event id is not managed by the gateway bridge");
        }
        long eventId = ticketingEventId.getLeastSignificantBits() & UUID_NODE_MASK;
        if (eventId <= 0) {
            throw new IllegalArgumentException("Ticketing event id does not contain a valid Event Service id");
        }
        return eventId;
    }

    private void setAuthorization(HttpHeaders headers, String authorization) {
        if (authorization != null && !authorization.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
    }

    private String authorizationOrServiceToken(String authorization) {
        if (authorization != null && !authorization.isBlank()) {
            return authorization;
        }
        return "Bearer " + createServiceToken();
    }

    private String createServiceToken() {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(300);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject("api-gateway-ticketing-adapter")
                .claim("roles", List.of("ADMIN"))
                .claim("scope", "events:read ticketing:write")
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims)).getTokenValue();
    }
}
