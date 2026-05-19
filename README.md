# API Gateway

Common entry point for the frontend and all Assignment 3 services.

## What It Does

- Provides fallback local auth for development.
- Routes primary login and registration to User Service.
- Accepts gateway-issued JWTs and User Service JWTs.
- Routes service APIs under `/api/**`.

## Routes

The gateway supports both the frontend `/api/...` routes and plain service-style routes such as `/events`, `/venues`, `/users`, `/tickets`, `/api/checkin/...`, and `/api/analytics/...`.

| Gateway path | Target |
| --- | --- |
| `/api/auth/register` | Gateway auth |
| `/api/auth/login` | Gateway auth |
| `/api/auth/me` | Gateway auth |
| `/api/bookings/**` | Booking |
| `/api/users/*/bookings` | Booking |
| `/api/payments/**` | Payment |
| `/api/bookings/{id}/payment` | Payment |
| `/api/events/*/tickettypes` | Ticketing through Event ID bridge |
| `/api/events/*/ticket-types` | Ticketing through Event ID bridge |
| `/api/events/**` | Event |
| `/api/ticket-types/**` | Ticketing |
| `/api/tickets/**` | Ticketing |
| `/api/venues/**` | Venue |
| `/api/users/**` | User |
| `/api/checkins/**` | Check-in |
| `/api/events/{id}/checkins` | Check-in |
| `/api/events/{id}/attendance` | Check-in |
| `/api/reports/**` | Reporting |

The full team Compose file points Event, Venue, User, Ticketing, Check-in, and Reporting routes to the teammate images. Numeric event ticket-type routes are bridged by the gateway because the current Event image uses numeric event IDs while the Ticketing image uses UUID event IDs. The bridge maps Event Service id `1` to Ticketing event id `00000000-0000-0000-0000-000000000001` and exposes an internal `/ticketing-event-adapter/events/{uuid}` endpoint so Ticketing can validate published Event Service events.

## Run

```bash
cd ../infra
docker compose up --build
```

Gateway URL:

```text
http://localhost:8080
```

## Important Environment Variables

| Variable | Meaning |
| --- | --- |
| `SPRING_R2DBC_URL` | Auth database connection |
| `JWT_ISSUER` | JWT issuer shared by all services |
| `JWT_SECRET` | HMAC JWT secret shared by all services |
| `BOOKING_SERVICE_URL` | Booking service URL |
| `PAYMENT_SERVICE_URL` | Payment service URL |
| `EVENT_SERVICE_URL` | Event service URL |
| `VENUE_SERVICE_URL` | Venue service URL |
| `USER_SERVICE_URL` | User service URL |
| `TICKETING_SERVICE_URL` | Ticketing service URL |
| `CHECKIN_SERVICE_URL` | Check-in service URL |
| `REPORTING_SERVICE_URL` | Reporting service URL |

## Tests

```bash
../booking-service/mvnw -f pom.xml test
```
