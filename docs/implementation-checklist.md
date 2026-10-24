# Implementation Checklist

Legend: `[x]` done and verified · `[~]` in progress · `[ ]` not started. A box is ticked only when the code compiles, its tests pass, and the behaviour was checked.

## Phase 0 — Discovery and feasibility
- [x] TfL endpoints verified against the live API (`docs/feasibility.md` §1)
- [x] Accessibility preference values confirmed from swagger + live calls
- [x] Station accessibility fields identified (LRAD `additionalProperties`)
- [x] Limitations and risks recorded (§2)
- [x] Library versions verified from Maven Central / npm (§3)
- [x] Architecture (`docs/architecture.md`), schema (`docs/database.md`), MVP (§6)

## Phase 1 — Backend foundation
- [x] Spring Boot 4.1 project via start.spring.io, Maven Wrapper
- [x] Docker Compose PostgreSQL + `.env.example`
- [x] Flyway configured, `ddl-auto=validate` (no migrations yet; the first tables arrive in Phase 3)
- [x] RFC 9457 error handling
- [x] Correlation-id filter + structured logging
- [x] Actuator health
- [x] TfL client (timeouts, retry, rate-limit handling, optional app key)
- [x] Station search endpoint (`GET /api/v1/stations?query=`)
- [x] Line status endpoint (`GET /api/v1/lines/status`)
- [x] Caffeine caches with stale fallback
- [x] Unit tests (mappers, services, client error handling) + Testcontainers context test
- [x] Verified against live TfL locally

## Phase 2 — Journey planning + frontend MVP
- [x] `GET /api/v1/journeys` with validated preferences
- [x] `GET /api/v1/stations/{id}` and `/accessibility` (incl. lift outages)
- [x] `GET /api/v1/disruptions`
- [x] React + Vite + TS strict + Tailwind scaffold
- [x] Layout, StationSearch, JourneySearchForm, AccessibilityPreferences, JourneyCard
- [x] Live status page
- [x] Journey details page
- [x] Loading / error / empty states
- [x] Frontend unit tests
- [x] Verified end-to-end against live TfL (headless browser smoke test of every page, 2026-10-24)

## Phase 3 — Authentication and persistence
- [x] `app_user`, Spring Session JDBC migrations
- [x] Register / login / logout / me / profile / delete
- [x] CSRF + session cookie config
- [x] Saved journeys CRUD with route lines/stations
- [x] Ownership checks + tests that user A can't touch user B's data
- [x] Login rate limiting
- [x] Frontend: sign in, register, account, saved journeys, save from journey details
- [x] Verified end-to-end in a browser against live TfL (2026-10-24)

## Phase 4 — Monitoring and notifications
- [ ] Disruption normalisation + identity/content hashing
- [ ] Scheduler with overlap guard, `monitoring_run` records
- [ ] Journey matching (lines + stations of chosen route)
- [ ] Alerts with DB-level dedup
- [ ] Email via Mailpit locally; delivery records + retries
- [ ] Notification preferences

## Phase 5 — UI refinement and mapping
- [ ] Leaflet map using TfL `lineString` geometry only
- [ ] Notification centre, dashboard, settings
- [ ] Accessibility audit (axe + keyboard + screen reader spot checks)

## Phase 6 — Production readiness
- [ ] GitHub Actions (backend + frontend), dependency scanning
- [ ] Dockerfiles
- [ ] Hosting verified (free tiers, same-origin API, scheduler wake strategy)
- [ ] Playwright E2E
- [ ] README, screenshots, final docs

## Known issues / notes
- The lift-disruption endpoint is undocumented by TfL (see `feasibility.md` §1.7).
- TfL cuts station accessibility notes (`AddtionalInformation`) at 255 characters. The UI marks cut-off notes as "TfL's text ends here" instead of showing them as if complete.
- TfL often publishes the same lift notice as both a lift disruption and a station disruption. The station page shows it once, under lift outages.
- Login rate limiting is in memory: limits are per instance and reset on restart. Fine for one instance; several would need a shared store.
- Behind a reverse proxy, the rate limiter sees the proxy's IP until forwarded headers are configured (`server.forward-headers-strategy`, Phase 6).
- Registration reveals whether an email is already registered (409). Unavoidable without email verification; login never reveals it.
- `GET /api/v1/auth/me` answers 401 when signed out, which browsers log in the console. Correct behaviour, not an error.
