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
- [x] Disruption normalisation + identity/content hashing (line status, station notices grouped across stations, lifts)
- [x] Scheduler with overlap guard (in-process flag + Postgres advisory lock), `monitoring_run` records
- [x] Journey matching (lines + stations of chosen route, station roles for lifts, hub expansion)
- [x] Alerts with DB-level dedup (`journey_alert_dedup` unique constraint, `ON CONFLICT DO NOTHING`)
- [x] Email via Mailpit locally; outbox delivery records + retries with backoff
- [x] Notification preferences (API + Account page)
- [x] Verified live against TfL with Mailpit (2026-10-24)

## Phase 5 — UI refinement and mapping
- [x] Leaflet map using TfL `lineString` geometry only (lazy-loaded; legs without geometry are listed, never drawn as straight lines)
- [x] Notification centre (`/alerts`, navbar unread count), dashboard (`/dashboard`), settings (alert settings on `/account`)
- [x] Accessibility audit: axe-core in unit tests (8 pages/components) and in a real browser with colour contrast (12 pages, desktop + mobile); keyboard-only journey planning and skip link tested
- [~] Screen reader: accessible names, roles and descriptions are asserted in tests; a manual VoiceOver/NVDA pass has not been done
- [x] Verified end-to-end in a browser against live TfL (2026-10-24)

## Phase 6 — Production readiness
- [x] GitHub Actions: backend (`mvnw verify` with Testcontainers), frontend (lint, types, tests, build), Playwright, Docker build; dependency review, weekly `npm audit`, Dependabot. All pass `actionlint`; first run on GitHub passed every job (2026-10-24)
- [x] Dockerfile: single multi-stage image (SPA + API, same origin), non-root, health check; verified locally with the live browser flow and no CSP violations
- [~] Hosting: free tiers checked (Render + Neon, 2026-10-24), same-origin by construction, wake strategy built (token-protected trigger + GitHub Actions cron) and tested; prod profile tuned so a serverless DB can sleep (pool drains to 0 connections, verified). **Not deployed**: needs the owner's Render and Neon accounts (`docs/deployment.md`)
- [x] Playwright E2E: 30 mocked-API tests (desktop + mobile) for CI, plus a live full-stack spec run against Vite and against the Docker image
- [x] README, screenshots, final docs

## Known issues / notes
- The lift-disruption endpoint is undocumented by TfL (see `feasibility.md` §1.7).
- TfL cuts station accessibility notes (`AddtionalInformation`) at 255 characters. The UI marks cut-off notes as "TfL's text ends here" instead of showing them as if complete.
- TfL often publishes the same lift notice as both a lift disruption and a station disruption. The station page shows it once, under lift outages.
- Login rate limiting is in memory: limits are per instance and reset on restart. Fine for one instance; several would need a shared store.
- Behind a reverse proxy, the rate limiter sees the proxy's IP until forwarded headers are configured (`server.forward-headers-strategy`, Phase 6).
- Registration reveals whether an email is already registered (409). Unavoidable without email verification; login never reveals it.
- `GET /api/v1/auth/me` answers 401 when signed out, which browsers log in the console. Correct behaviour, not an error.
- Line-status alerts are per line: TfL's status gives the affected section only as free text ("between Harrow-on-the-Hill and Uxbridge"), so a journey on an unaffected part of the line is still alerted. The alert shows TfL's text so the user can judge.
- A heading-less station notice whose text TfL edits is seen as resolved + new (there is nothing else stable to key it on).
- Email delivery is at-least-once: a crash between SMTP accepting a message and the row being marked SENT re-sends it.
- Monitoring only runs while the backend process is awake (risk R1, Phase 6).
- The route map uses OpenStreetMap's public tiles, which suit low traffic only (OSM tile policy, checked 2026-10-24). Heavy use would need a tile provider. Tests never request real tiles.
- `server.forward-headers-strategy` is set in the prod profile (resolves the Phase 3 note on the rate limiter behind a proxy).
- Content-Security-Policy allows `'unsafe-inline'` styles because Leaflet positions tiles with inline styles; scripts are `'self'` only.
- The main JS bundle is ~520 kB (160 kB gzipped); Leaflet is split out and loaded only on journey details.
