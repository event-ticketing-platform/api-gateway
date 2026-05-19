package ee.ut.eventticketing.api_gateway;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;

@RestController
public class TicketingEventBridgeController {

    private static final long UUID_NODE_MASK = 0x0000FFFFFFFFFFFFL;
    private static final long UUID_PREFIX_MASK = 0xFFFF000000000000L;

    private final WebClient eventClient;
    private final WebClient ticketingClient;

    public TicketingEventBridgeController(
            WebClient.Builder webClientBuilder,
            @Value("${services.event.base-url}") String eventBaseUrl,
            @Value("${services.ticketing.base-url}") String ticketingBaseUrl) {
        this.eventClient = webClientBuilder.clone().baseUrl(eventBaseUrl).build();
        this.ticketingClient = webClientBuilder.clone().baseUrl(ticketingBaseUrl).build();
    }

    @GetMapping({
            "/api/events/{eventId}/tickettypes",
            "/api/events/{eventId}/ticket-types",
            "/events/{eventId}/tickettypes",
            "/events/{eventId}/ticket-types"
    })
    public Mono<ResponseEntity<Object>> listTicketTypes(@PathVariable long eventId) {
        UUID ticketingEventId = toTicketingEventId(eventId);
        return ticketingClient.get()
                .uri("/events/{eventId}/tickettypes", ticketingEventId)
                .retrieve()
                .toEntity(Object.class)
                .onErrorResume(WebClientResponseException.NotFound.class,
                        ignored -> Mono.just(ResponseEntity.ok(List.of())));
    }

    @PostMapping({
            "/api/events/{eventId}/tickettypes",
            "/api/events/{eventId}/ticket-types",
            "/events/{eventId}/tickettypes",
            "/events/{eventId}/ticket-types"
    })
    public Mono<ResponseEntity<Object>> createTicketType(
            @PathVariable long eventId,
            @RequestBody Map<String, Object> body) {
        UUID ticketingEventId = toTicketingEventId(eventId);
        return ticketingClient.post()
                .uri("/events/{eventId}/tickettypes", ticketingEventId)
                .bodyValue(body)
                .retrieve()
                .toEntity(Object.class);
    }

    @GetMapping("/ticketing-event-adapter/events/{ticketingEventId}")
    public Mono<ResponseEntity<Object>> getEventForTicketing(@PathVariable UUID ticketingEventId) {
        long eventId = fromTicketingEventId(ticketingEventId);
        return eventClient.get()
                .uri("/events/{eventId}", eventId)
                .retrieve()
                .toEntity(Object.class);
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
}
