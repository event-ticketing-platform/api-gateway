package ee.ut.eventticketing.api_gateway;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final WebClient webClient;
    private final String bookingServiceUrl;
    private final String paymentServiceUrl;
    private final String ticketingServiceUrl;

    public DemoController(
            WebClient.Builder webClientBuilder,
            @Value("${services.booking.base-url}") String bookingServiceUrl,
            @Value("${services.payment.base-url}") String paymentServiceUrl,
            @Value("${services.ticketing.base-url}") String ticketingServiceUrl) {
        this.webClient = webClientBuilder.build();
        this.bookingServiceUrl = bookingServiceUrl;
        this.paymentServiceUrl = paymentServiceUrl;
        this.ticketingServiceUrl = ticketingServiceUrl;
    }

    @GetMapping
    public Mono<Map<String, Object>> demo() {
        Mono<String> booking = health(bookingServiceUrl);
        Mono<String> payment = health(paymentServiceUrl);
        Mono<String> ticketing = health(ticketingServiceUrl);

        return Mono.zip(booking, payment, ticketing)
                .map(tuple -> Map.of(
                        "message", "API call success",
                        "gateway", "UP",
                        "bookingService", tuple.getT1(),
                        "paymentService", tuple.getT2(),
                        "ticketingService", tuple.getT3(),
                        "timestamp", LocalDateTime.now().toString()));
    }

    @GetMapping("/gateway")
    public Mono<Map<String, Object>> gateway() {
        return Mono.just(Map.of(
                "message", "Gateway API call success",
                "gateway", "UP",
                "timestamp", LocalDateTime.now().toString()));
    }

    @GetMapping("/booking")
    public Mono<Map<String, Object>> booking() {
        return health(bookingServiceUrl)
                .map(status -> Map.of(
                        "message", "Booking service API call success",
                        "bookingService", status,
                        "timestamp", LocalDateTime.now().toString()));
    }

    @GetMapping("/payment")
    public Mono<Map<String, Object>> payment() {
        return health(paymentServiceUrl)
                .map(status -> Map.of(
                        "message", "Payment service API call success",
                        "paymentService", status,
                        "timestamp", LocalDateTime.now().toString()));
    }

    @GetMapping("/ticketing")
    public Mono<Map<String, Object>> ticketing() {
        return health(ticketingServiceUrl)
                .map(status -> Map.of(
                        "message", "Ticketing service API call success",
                        "ticketingService", status,
                        "timestamp", LocalDateTime.now().toString()));
    }

    @GetMapping("/booking-payment")
    public Mono<Map> bookingPaymentIntegration() {
        return webClient.post()
                .uri(bookingServiceUrl + "/demo/bookings/payment-check")
                .retrieve()
                .bodyToMono(Map.class);
    }

    private Mono<String> health(String baseUrl) {
        return webClient.get()
                .uri(baseUrl + "/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .map(body -> String.valueOf(body.getOrDefault("status", "UNKNOWN")))
                .onErrorReturn("DOWN");
    }
}
