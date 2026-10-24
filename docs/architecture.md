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

## Monitoring and alerts (Phase 4)

A scheduled cycle (`MonitoringScheduler`, every 2 minutes by default, `fixedDelay` so cycles never overlap) runs `MonitoringService.runCycle()`, then sends queued emails.

```
DisruptionFeed.read()            ── no transaction; same caches as the UI
  line status · station notices · lifts  →  DisruptionNormaliser  →  ObservedDisruption(identityKey, contentHash, lines, stations)
        │  a feed that fails or is stale is "not read": its events are neither created nor resolved
        ▼
one short transaction:
  pg_try_advisory_xact_lock      ── a second instance skips instead of waiting
  ChangeDetector.diff(active, snapshot)  →  created / updated (revision+1) / unchanged / resolved
  JourneyMatcher.match(monitored journeys, active events)
  INSERT journey_alert … ON CONFLICT DO NOTHING   ── NEW, UPDATED or RESOLVED, once per (journey, event, type, revision)
  INSERT notification_delivery (outbox) if the user has email on
        ▼
monitoring_run row (SUCCEEDED / PARTIAL / FAILED / SKIPPED)
EmailDispatcher: claim due rows (FOR UPDATE SKIP LOCKED) → send with no transaction open → SENT, or retry after 1m/5m/30m, then FAILED
```

| Rule | Why |
|---|---|
| Identity: line id; station notice type + start + heading + lines; lift station id | TfL has no ids. Severity change on a line is an *update*, not a new disruption |
| Station notices grouped across stations | TfL repeats one notice at every station it concerns (141 rows, 62 texts on 2026-10-24) |
| Lines inferred from a "CIRCLE LINE:" heading when TfL lists none | Otherwise a Circle line closure listed at Victoria would alert Victoria line journeys |
| Lift outages match only ORIGIN / INTERCHANGE / DESTINATION stops | Riding through a station doesn't use its lifts |
| Hub ids in the lift feed (`HUBWMB`) expanded to member stations | Saved routes hold platform-level ids from journey legs |
| Lift notice repeated as a station notice → one alert | TfL publishes both with identical text |
| Unreadable or stale feed never resolves events | "TfL didn't answer" is not "the disruption is over" |
| Plain JDBC (`JdbcClient`) in monitoring/notification | Set-based work and `ON CONFLICT`; user-facing CRUD stays on JPA |
| Email outbox in the alert's transaction | An alert can't exist without its email being queued, and SMTP is never called inside a transaction |

Users control monitoring per journey (saved-journey switch) and globally, plus email, in `notification_preference` (absent row = email off, monitoring on).

## Frontend additions (Phase 5)

- **Map**: `JourneyMapSection` lazy-loads `JourneyMap` (React Leaflet) so Leaflet's ~155 kB only loads on journey details. It draws `leg.path` from TfL and nothing else; legs without a shape are counted in a note. The map has an accessible name pointing to the step-by-step directions, which are the text equivalent.
- **Alerts**: `/alerts` (notification centre) and `/dashboard` poll every 60 s while open; the navbar shows the unread count. `MonitoringNote` says when journeys were last checked, so an empty list can't be mistaken for "all clear" if checks stopped.
- User-scoped caches (`saved-journeys`, `notifications`) are dropped on sign-in and sign-out.

## Deployment (Phase 6)

One image serves the SPA and the API from the same origin (see [deployment.md](deployment.md)). `SpaWebConfig` falls back to `index.html` for extension-less paths outside `/api`, `/actuator`, `/v3`, `/swagger-ui` and `/internal`, so unknown API paths stay errors. Security headers: CSP (`script-src 'self'`, tiles from OSM only), `Referrer-Policy: strict-origin-when-cross-origin` (OSM needs a Referer; only the origin is sent), `X-Frame-Options: DENY`, `nosniff`. `POST /internal/monitoring/run` (token, constant-time compare, no session/CSRF) lets an external cron wake a sleeping host.

## Cross-cutting decisions

| Concern | Decision |
|---|---|
| Errors | RFC 9457 `application/problem+json` via `ProblemDetail`; stable `type` URIs; no stack traces to clients |
| HTTP client | Spring `RestClient` with connect/read timeouts; retries only on idempotent GETs for 5xx/timeouts/429, with backoff |
| Caching | Caffeine, per-cache TTLs (see `feasibility.md` §4) |
| Observability | Actuator health/metrics, `X-Correlation-Id` in MDC and logs, Micrometer timers on TfL calls |
| Auth | Server-side sessions (Spring Session JDBC), HttpOnly cookie, CSRF cookie pattern |
| Scheduling | `@Scheduled` `fixedDelay`; `AtomicBoolean` per instance, Postgres advisory lock across instances, unique constraint as the final dedup guarantee |
| Time | All timestamps stored and transmitted as UTC `Instant`; UI renders Europe/London |

This document is updated as each phase lands.
