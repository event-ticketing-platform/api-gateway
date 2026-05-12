package ee.ut.eventticketing.api_gateway;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class DemoController {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<Map<String, Object>> STRING_OBJECT_MAP =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;
    private final String bookingServiceUrl;
    private final String paymentServiceUrl;
    private final String ticketingServiceUrl;
    private final String userServiceUrl;

    public DemoController(
            WebClient.Builder webClientBuilder,
            @Value("${services.booking.base-url}") String bookingServiceUrl,
            @Value("${services.payment.base-url}") String paymentServiceUrl,
            @Value("${services.ticketing.base-url}") String ticketingServiceUrl,
            @Value("${services.user.base-url}") String userServiceUrl) {
        this.webClient = webClientBuilder.build();
        this.bookingServiceUrl = bookingServiceUrl;
        this.paymentServiceUrl = paymentServiceUrl;
        this.ticketingServiceUrl = ticketingServiceUrl;
        this.userServiceUrl = userServiceUrl;
    }

    @GetMapping("/demo")
    public Mono<Map<String, Object>> demo() {
        Mono<String> booking = health(bookingServiceUrl);
        Mono<String> payment = health(paymentServiceUrl);
        Mono<String> ticketing = health(ticketingServiceUrl);
        Mono<String> user = health(userServiceUrl);

        return Mono.zip(booking, payment, ticketing, user)
                .map(tuple -> Map.of(
                        "message", "API call success",
                        "gateway", "UP",
                        "bookingService", tuple.getT1(),
                        "paymentService", tuple.getT2(),
                        "ticketingService", tuple.getT3(),
                        "userService", tuple.getT4(),
                        "timestamp", LocalDateTime.now().toString()));
    }

    @GetMapping({"/demo/gateway", "/operations/gateway/status"})
    public Mono<Map<String, Object>> gateway() {
        return Mono.just(Map.of(
                "message", "Gateway API call success",
                "gateway", "UP",
                "timestamp", LocalDateTime.now().toString()));
    }

    @GetMapping({"/demo/booking", "/operations/services/booking/status"})
    public Mono<Map<String, Object>> booking() {
        return health(bookingServiceUrl)
                .map(status -> Map.of(
                        "message", "Booking service API call success",
                        "bookingService", status,
                        "timestamp", LocalDateTime.now().toString()));
    }

    @GetMapping({"/demo/payment", "/operations/services/payment/status"})
    public Mono<Map<String, Object>> payment() {
        return health(paymentServiceUrl)
                .map(status -> Map.of(
                        "message", "Payment service API call success",
                        "paymentService", status,
                        "timestamp", LocalDateTime.now().toString()));
    }

    @GetMapping({"/demo/ticketing", "/operations/services/ticketing/status"})
    public Mono<Map<String, Object>> ticketing() {
        return health(ticketingServiceUrl)
                .map(status -> Map.of(
                        "message", "Ticketing service API call success",
                        "ticketingService", status,
                        "timestamp", LocalDateTime.now().toString()));
    }

    @GetMapping({"/demo/user", "/operations/services/user/status"})
    public Mono<Map<String, Object>> user() {
        return health(userServiceUrl)
                .map(status -> Map.of(
                        "message", "User service API call success",
                        "userService", status,
                        "timestamp", LocalDateTime.now().toString()));
    }

    @GetMapping({"/demo/booking-payment", "/operations/bookings/payment-check"})
    public Mono<Map<String, Object>> bookingPaymentIntegration() {
        return webClient.post()
                .uri(bookingServiceUrl + "/demo/bookings/payment-check")
                .retrieve()
                .bodyToMono(STRING_OBJECT_MAP);
    }

    @GetMapping({"/demo/booking-table-data", "/operations/bookings/summary"})
    public Mono<Map<String, Object>> bookingTableData() {
        return bookingRows()
                .map(rows -> Map.of("rows", rows));
    }

    @PostMapping("/operations/bookings/checkout")
    public Mono<Map<String, Object>> checkoutBooking(@RequestBody Map<String, Object> request) {
        return createBookingAndPayment(request);
    }

    @PostMapping("/demo/book-selected")
    public Mono<Map<String, Object>> bookSelected(@RequestBody Map<String, Object> request) {
        return createBookingAndPayment(request)
                .flatMap(createdBooking -> bookingRows()
                        .map(rows -> Map.of(
                                "createdBooking", createdBooking,
                                "rows", rows)));
    }

    private Mono<Map<String, Object>> createBookingAndPayment(Map<String, Object> request) {
        return webClient.post()
                .uri(bookingServiceUrl + "/demo/bookings/book-and-pay")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(STRING_OBJECT_MAP);
    }

    private Mono<List<Map<String, Object>>> bookingRows() {
        return webClient.get()
                .uri(bookingServiceUrl + "/demo/bookings")
                .retrieve()
                .bodyToMono(LIST_OF_MAPS)
                .flatMapMany(reactor.core.publisher.Flux::fromIterable)
                .flatMap(this::toBookingRow)
                .collectList();
    }

    private Mono<Map<String, Object>> toBookingRow(Map<String, Object> booking) {
        Object bookingId = booking.get("bookingId");

        return webClient.get()
                .uri(paymentServiceUrl + "/payments/booking/{bookingId}", bookingId)
                .retrieve()
                .bodyToMono(LIST_OF_MAPS)
                .onErrorReturn(List.of())
                .map(payments -> {
                    Map<String, Object> payment = firstPayment(payments);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("bookingId", bookingId);
                    row.put("totalAmount", booking.get("totalAmount"));
                    row.put("currency", booking.get("currency"));
                    row.put("bookingStatus", booking.get("status"));
                    row.put("paymentId", payment.get("paymentId"));
                    row.put("paymentStatus", Objects.toString(payment.get("status"), "NOT_STARTED"));
                    return row;
                });
    }

    private Map<String, Object> firstPayment(List<Map<String, Object>> payments) {
        if (payments.isEmpty()) {
            return Map.of();
        }

        return payments.get(0);
    }

    private Mono<String> health(String baseUrl) {
        return webClient.get()
                .uri(baseUrl + "/actuator/health")
                .retrieve()
                .bodyToMono(STRING_OBJECT_MAP)
                .map(body -> String.valueOf(body.getOrDefault("status", "UNKNOWN")))
                .onErrorReturn("DOWN");
    }
}
