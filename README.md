# API Gateway

Common entry point for the frontend and all Assignment 3 services.

## What It Does

- Provides fallback local auth for development.
- Routes primary login and registration to User Service.
- Accepts gateway-issued JWTs and User Service JWTs.
- Routes service APIs under `/api/**`.

## Routes

| Gateway path | Target |
| --- | --- |
| `/api/auth/register` | Gateway auth |
| `/api/auth/login` | Gateway auth |
| `/api/auth/me` | Gateway auth |
| `/api/bookings/**` | Booking |
| `/api/users/*/bookings` | Booking |
| `/api/payments/**` | Payment |
| `/api/bookings/{id}/payment` | Payment |
| `/api/events/*/tickettypes` | Ticketing, or Booking in standalone mode |
| `/api/events/*/ticket-types` | Ticketing, or Booking in standalone mode |
| `/api/events/**` | Event |
| `/api/ticket-types/**` | Ticketing, or Booking in standalone mode |
| `/api/tickets/**` | Ticketing |
| `/api/venues/**` | Venue |
| `/api/users/**` | User |
| `/api/checkins/**` | Check-in |
| `/api/events/{id}/checkins` | Check-in |
| `/api/events/{id}/attendance` | Check-in |
| `/api/reports/**` | Reporting |

The base Compose file points Event and User routes to the teammate images. Ticketing still falls back to Booking until that service is available.

## Run

```bash
cd ../infra
docker compose up --build
```

Gateway URL:

```text
http://localhost:18080
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
