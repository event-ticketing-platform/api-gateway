# API Gateway

The API Gateway is the single entry point for the frontend.

Instead of the Vue app calling every microservice directly, it calls the gateway. The gateway then routes requests to Booking Service or Payment Service.

## What It Does

- Routes booking API calls to Booking Service.
- Routes payment API calls to Payment Service.
- Provides simple demo health endpoints for the frontend buttons.
- Handles CORS for local frontend development.

## Main Routes

| Frontend/Gateway Path | Goes To |
| --- | --- |
| `/api/bookings/**` | Booking Service `/bookings/**` |
| `/api/users/*/bookings` | Booking Service `/users/*/bookings` |
| `/api/payments/**` | Payment Service `/payments/**` |
| `/api/demo/gateway` | Gateway health/demo response |
| `/api/demo/booking` | Booking Service health check |
| `/api/demo/payment` | Payment Service health check |
| `/api/demo/booking-payment` | Booking Service creates a booking and calls Payment Service |

## Run With The Full System

From the infra folder:

```bash
cd ../infra
docker compose up --build
```

The gateway runs on:

```text
http://localhost:8080
```

## Run Locally

This service does not have its own Maven wrapper. Use the wrapper from another Spring service:

```bash
../booking-service/mvnw spring-boot:run
```

## Demo Checks

```bash
curl http://localhost:8080/api/demo/gateway
curl http://localhost:8080/api/demo/booking
curl http://localhost:8080/api/demo/payment
curl http://localhost:8080/api/demo/booking-payment
```

## Important Environment Variables

| Variable | Default | Meaning |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Gateway port |
| `BOOKING_SERVICE_URL` | `http://localhost:8081` | Booking Service URL |
| `PAYMENT_SERVICE_URL` | `http://localhost:8082` | Payment Service URL |
| `CORS_ALLOWED_ORIGINS` | local frontend URLs | Frontend origins allowed by CORS |

## Tests / Build Check

```bash
../booking-service/mvnw test
```
