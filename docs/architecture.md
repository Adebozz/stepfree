# Architecture

StepFree London is a **modular monolith**: one Spring Boot application, one PostgreSQL database, one React single-page app. It is deliberately not split into services. There is one team, one deployable, and no independent scaling need, so microservices would add network failure modes without benefit.

```
┌──────────────────────────┐        same origin: /api/*        ┌─────────────────────────────────────────┐
│  React SPA (Vite build)  │ ────────────────────────────────▶ │  Spring Boot (Java 21)                  │
│  TanStack Query cache    │   HttpOnly SESSION cookie + CSRF  │                                         │
└──────────────────────────┘                                   │  station · line · journey · disruption  │
                                                               │  accessibility · auth · savedjourney    │
                                                               │  monitoring · notification              │
                                                               │            │                │           │
                                                               │       tfl (client)     Caffeine cache   │
                                                               └────────────┬────────────────┬───────────┘
                                                                            │ HTTPS          │ JDBC
                                                                            ▼                ▼
                                                                  TfL Unified API      PostgreSQL 17
                                                                                       (Flyway-managed)
```

## Backend package layout (by domain)

```
com.stepfreelondon
├── StepfreeLondonApplication
├── common/          error handling (RFC 9457 ProblemDetail), correlation-id filter, clock
├── config/          security, CORS, cache, OpenAPI, HTTP client configuration
├── tfl/             TfL client + raw TfL response records (package-private where possible)
├── station/         controller, service, DTOs, mapper, entity, repository
├── line/            line status controller/service/DTOs
├── journey/         journey planning controller/service/DTOs, preference enums
├── disruption/      disruption retrieval, normalisation, fingerprinting, persistence
├── accessibility/   station accessibility + lift outages
├── auth/            registration, login, current user
├── user/            user entity, profile, account deletion
├── savedjourney/    saved journeys + their route lines/stations
├── monitoring/      scheduler, change detection, journey matching
└── notification/    alerts, preferences, email delivery
```

Each domain owns its controller → service → repository chain and its DTOs. Only the `tfl` package knows TfL's JSON shapes; every other package depends on internal DTOs that the `tfl` mappers produce. If TfL changes a field, only `tfl` changes.

## Request flow example: `GET /api/v1/journeys`

1. `JourneyController` binds and validates `JourneySearchRequest` (Bean Validation). No logic.
2. `JourneyService` checks origin ≠ destination, builds the TfL query, and asks the cache.
3. On a cache miss, `TflClient.journeyResults(...)` calls TfL with a timeout and bounded retry.
4. `JourneyMapper` converts TfL legs into `JourneyOptionDto` (legs, lines, obstacles, geometry, disruptions).
5. A response wrapper adds `retrievedAt`/`stale`.
6. Any failure becomes a `ProblemDetail` through `GlobalExceptionHandler`.

## Cross-cutting decisions

| Concern | Decision |
|---|---|
| Errors | RFC 9457 `application/problem+json` via `ProblemDetail`; stable `type` URIs; no stack traces to clients |
| HTTP client | Spring `RestClient` with connect/read timeouts; retries only on idempotent GETs for 5xx/timeouts/429, with backoff |
| Caching | Caffeine, per-cache TTLs (see `feasibility.md` §4) |
| Observability | Actuator health/metrics, `X-Correlation-Id` in MDC and logs, Micrometer timers on TfL calls |
| Auth | Server-side sessions (Spring Session JDBC), HttpOnly cookie, CSRF cookie pattern |
| Scheduling | `@Scheduled` with a single-instance guard (`AtomicBoolean` + DB-level idempotency); multi-instance requires ShedLock first |
| Time | All timestamps stored and transmitted as UTC `Instant`; UI renders Europe/London |

This document is updated as each phase lands.
