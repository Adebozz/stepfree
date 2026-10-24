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
├── auth/            user entity, registration, login/logout, profile, password, account deletion, login rate limit
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

## Authentication (Phase 3)

Server-side sessions, not JWTs: the SPA and API share an origin, so a cookie is the simplest secure option, and a session can be revoked instantly (logout, password change, account deletion), which a stateless token can't.

| Concern | How |
|---|---|
| Session storage | Spring Session JDBC (`spring_session` tables), 7-day idle timeout |
| Cookie | `SESSION`, `HttpOnly` (unreadable by scripts), `SameSite=Lax`, `Secure` in production (`SESSION_COOKIE_SECURE`) |
| CSRF | Spring Security `spa()`: readable `XSRF-TOKEN` cookie echoed as `X-XSRF-TOKEN`. Required on every POST/PATCH/DELETE, login included. Token rotated on sign-in |
| Login | JSON endpoint; `SessionAuthenticator` does what form login would: change session id (fixation), save the security context, rotate CSRF |
| Principal | `AuthUser` holds only the user id; `getUsername()` is the id, so Spring Session's index finds every session of a user and email changes don't orphan them. Password hash erased after login |
| Passwords | Delegating encoder (bcrypt, `{bcrypt}` prefix allows future upgrades); 12–72 characters and ≤ 72 bytes |
| Enumeration | Unknown email and wrong password give the same 401; `DaoAuthenticationProvider` hashes even for unknown emails, so timing matches |
| Brute force | `LoginRateLimiter`: 5 failures per email and 50 per IP per 15 minutes → 429 + `Retry-After`. Also guards password re-confirmation |
| Sensitive changes | Changing email or password, and deleting the account, need the current password. Password change signs out other sessions |

**Ownership.** Saved journeys carry `user_id`; the owner always comes from the session, never the request. `SavedJourneyRepository` extends the bare `Repository` (no inherited `findById`), and every lookup is `findByIdAndUserId`. Another user's journey returns **404, not 403**, so ids can't be probed.

**Saving a journey.** The client sends the chosen option's route (lines, stops with roles). Origin and destination are resolved through TfL (cached) to confirm they exist and to store their ICS codes; that call happens **outside** any database transaction, then the insert runs in a short `TransactionTemplate`.

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
