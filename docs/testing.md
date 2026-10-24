# Testing

## Running

```bash
cd backend
./mvnw test        # all backend tests; Docker must be running (Testcontainers)

cd frontend
npm test           # Vitest + Testing Library (jsdom)
```

The suite makes **no live TfL calls**. TfL fixtures in `backend/src/test/resources/tfl/` are real responses captured from the live API on 2026-10-23, so mapping tests run against TfL's actual JSON shape.

## Backend suite (Phase 6): 172 tests, all passing

Added in Phase 6: `SpaAndSecurityHeadersTest` (5: client routes get the app shell; `/api`, `/actuator` and missing files never do; CSP/Referrer/nosniff/frame headers; trigger is 404 without a token), `MonitoringTriggerTest` (2: right token runs a cycle without session or CSRF; missing/wrong tokens get 401 and run nothing), `ProdProfileTest` (1: the app boots with `application-prod.yml`; it caught a real startup failure, a Hikari `idle-timeout` given as `60s`).

## Backend suite (Phase 4): 164 tests

| Test class | Type | Covers |
|---|---|---|
| `TflClientTest` (18) | Unit, `MockRestServiceServer` | Parsing real fixtures; path encoding of user input (no path traversal); app-key handling; retry/backoff on 5xx and timeouts; no retry on 404/400/401/403/429/420; unreadable bodies; latency metric; app-key redaction |
| `LiveDataCacheTest` (8) | Unit, fake clock | TTL hits/misses; stale fallback with original `retrievedAt`; rethrow when nothing cached; non-transient errors not hidden; stale max age |
| `LineStatusMapperTest` (20) | Unit | Real snapshot mapping; missing status → `UNKNOWN`, never Good Service; most-serious-first; step-free flag; validity periods; every severity code |
| `StationMapperTest` (5) | Unit | Real Wembley search; hubs/ICS codes; name cleanup; missing fields |
| `TflDateTimesTest` (3) | Unit | UTC vs London-local timestamps across BST/GMT; TfL placeholder dates |
| `StationControllerTest` (10) | `@WebMvcTest` + real security config | Response shape; validation problems; 503/Retry-After; generic 500 with no leaked details; 401 problem for protected routes; correlation id |
| `LineStatusControllerTest` (2) | `@WebMvcTest` | Stale flag surfaced; outage → 503 |
| `StepfreeLondonApplicationTests` (4) | `@SpringBootTest` + Testcontainers PostgreSQL 17 | Full context boots, Flyway runs, health UP with details hidden, non-public actuator endpoints not exposed, OpenAPI document |

Added in Phase 2:

| Test class | Type | Covers |
|---|---|---|
| `JourneyMapperTest` (7) | Unit | Real step-free and disrupted journey fixtures; legs, obstacles, fares, line ids |
| `JourneyServiceTest` (6) | Unit | Preferences passed to TfL unchanged; caching; no-journey handling |
| `JourneyControllerTest` (7) | `@WebMvcTest` | ICS-code validation; same-station rejection; enum binding; problem responses |
| `StationProfileMapperTest` (3) | Unit | LRAD accessibility fields; missing values stay `null` (unavailable), never "No" |
| `StationProfileServiceTest` (4) | Unit | Hub member merging; only this station's lift outages; live-source failure reported, not hidden; hub-notice and lift-notice de-duplication |
| `DisruptionMapperTest` (4) | Unit | StopPoint disruptions and lift outages; accessibility keyword flag |

Added in Phase 3:

| Test class | Type | Covers |
|---|---|---|
| `AuthIntegrationTest` (14) | `@SpringBootTest(RANDOM_PORT)` + Testcontainers, real HTTP via `ApiClient` (cookie jar + SPA CSRF) | Register signs in with `HttpOnly`/`SameSite=Lax` cookie; bcrypt hash, none in the session row; CSRF required; login replaces an existing session id (old id gone from `spring_session`); case-insensitive email; wrong password ≡ unknown email; 6th failure → 429 + `Retry-After` even with the right password; duplicate email 409; validation and 72-byte limit; logout; email change needs password and keeps the session; password change signs out other sessions; account deletion removes user and every session |
| `SavedJourneyIntegrationTest` (8) | Same, TfL mocked with captured fixtures | CRUD with `Location`, ICS codes from TfL, duplicate stops collapsed, route unchanged by PATCH; **user B gets 404 on A's journey for read/update/delete, identical to a missing id**; names unique per user, not globally; stations stored once; unknown TfL station → 400; malformed input → 400 with field paths; 401 when signed out; account deletion cascades |
| `LoginRateLimiterTest` (4) | Unit, controllable clock | Per-email block with exact `Retry-After`; window expiry; per-IP spraying; success clears email count but not IP count |

`ApiClient` (test helper) uses the JDK `HttpClient` with a `CookieManager` and echoes `XSRF-TOKEN` exactly as the SPA does, so these tests exercise the real session and CSRF machinery instead of MockMvc's `csrf()` shortcut.

Added in Phase 4:

| Test class | Type | Covers |
|---|---|---|
| `DisruptionNormaliserTest` (9) | Unit, real fixtures (incl. the full mode-wide station feed captured 2026-10-24) | Only disrupted lines become events; severity change keeps identity, changes hash; whitespace isn't a change; one event per notice across 18 stations; line inferred from "CIRCLE LINE:" heading; identity independent of feed order; lifts one event per station |
| `ChangeDetectorTest` (7) | Unit | Created / updated / unchanged / resolved; a feed that wasn't read never resolves; same key in two sources; duplicate keys; scope change without a revision |
| `JourneyMatcherTest` (5) | Unit | Lines ridden only; station notice needs a ridden line if it names lines; lifts only where the passenger leaves the train; lift + identical station notice → one alert |
| `DisruptionFeedTest` (3) | Unit | Stale, unavailable and failing feeds reported as not read; hub ids expanded, with fallback when TfL fails |
| `AlertEmailTest` (2) | Unit | Email content and links; CR/LF in TfL text can't inject headers; subject length |
| `MonitoringIntegrationTest` (11) | Testcontainers + real HTTP | NEW → UPDATED → RESOLVED, each once however many cycles run; partial and total feed failure; paused journeys and opted-out users get nothing; **4 concurrent cycles produce one alert**; outbox retry with backoff, not retried early, cancelled when email turned off; alerts are per user (404 on another user's alert); preferences validation; monitoring status; account deletion cascades |

A mutation check was run: removing the "already alerted at this revision" check makes two integration tests fail.

## Frontend suite (Phase 5): 86 tests, all passing

Added in Phase 5: `geometry.test.ts` (4: only TfL shapes drawn, bad points dropped, stops, bounds), `JourneyMapSection.test.tsx` (2: no invented map when TfL has no geometry), `AlertsPage.test.tsx` (4), `DashboardPage.test.tsx` (1), `Navbar.test.tsx` (2: unread count in the link name, no alert requests when signed out), `a11y.test.tsx` (7: axe-core on every page; colour contrast is checked in the browser). Pages under test also run axe.

## Browser suite (Playwright, Phase 5): 30 tests, all passing

`npm run test:e2e`: Chromium desktop and Pixel 7, against the Vite dev server with the API mocked in the browser (`e2e/mockApi.ts`, built from the same fixtures as the unit tests), so it needs no backend or TfL and runs in CI. Map tiles are never fetched.

| Spec | Covers |
|---|---|
| `accessibility.spec.ts` | axe-core WCAG 2.1 A/AA **including colour contrast** on 12 pages at both sizes; skip link moves focus to main; a journey can be planned with the keyboard alone |
| `flows.spec.ts` | Guarded `/alerts` → sign in (wrong password message) → back to alerts → mark read updates the navbar count → turn email alerts on; plan → map draws TfL's shape and markers with OSM attribution → save |

`npm run test:e2e:live` (`e2e/live/full-stack.spec.ts`) drives the real stack against live TfL: register, turn on email, plan and save Wembley Park → King's Cross, wait for the scheduler's alert, read it, check the email count in Mailpit, delete the account.

The audit found and fixed: heading levels skipping (station page h2 → h4; alerts h1 → h3); the navbar link announced as "Alerts , 1 unread" in Chromium (sr-only span) — now a single `aria-label`.

## Frontend suite (Phase 4): 66 tests, all passing

| File | Covers |
|---|---|
| `StationSearch.test.tsx` (5) | Debounced search, ARIA combobox keyboard navigation, selection |
| `JourneySearchForm.test.tsx` (5) | Validation, accessibility preferences, swap, submit |
| `JourneyCard.test.tsx` (2) | Times, lines, obstacle wording ("reported by TfL") |
| `StationInformation.test.tsx` (6) | Missing data shown as "Information unavailable"; missing lift data never read as "all working"; cut-off TfL notes marked; "no other disruptions" wording |
| `StatusComponents.test.tsx` (8) | Service-level badges, status summary, freshness notes |
| `useJourneyLineStatuses.test.tsx` (2) | Matching live line status to a journey's lines |
| `search.test.ts` (6) | URL ⇄ form state ⇄ API parameter round-trips |
| `utils.test.ts` (7) | London-time formatting across BST/GMT, line colours/contrast, TfL note truncation |
| `client.test.ts` (4) | `apiSend` fetches a CSRF token when missing and echoes it; reuses an existing one; problem → `ApiError`; `safeNextPath` rejects off-site redirects |
| `route.test.ts` (6) | Route roles (origin, intermediate, interchange, destination), hub ids kept, invalid ids dropped, walking-only journeys, name suggestion, re-plan needs ICS codes |
| `SignInPage.test.tsx` (4) | Signs in and returns to `next`; shows the server's message; ignores an off-site `next`; validates before calling the server |
| `SaveJourneyPanel.test.tsx` (3) | Signed-out users get a sign-in link back to this page; saves the route and preferences; explains a duplicate name |
| `AccountPage.test.tsx` (2) | Alert settings: defaults, accessible hints, optimistic save with both fields, rollback with the server's message on error |
| `SavedJourneysPage.test.tsx` (6) | Guard redirects to sign-in; list with re-plan link; delete needs confirmation; empty state; rename keeps the card's accessible name; optimistic monitoring toggle rolls back on error |

## Manual verification performed (Phase 6, 2026-10-24)

Production image (`docker compose --profile app up --build`), prod profile, live TfL, Mailpit:
- Healthy, running as user `stepfree`, JSON logs; `/saved` serves the app shell; Swagger UI 404; CSP and Referrer-Policy present.
- `POST /internal/monitoring/run`: 401 without a token, `SUCCEEDED` with it.
- `E2E_BASE_URL=http://localhost:8080 E2E_TRIGGER_TOKEN=… npm run test:e2e:live`: passed with no console errors (so no CSP violations).
- The first image build failed to start (Hikari rejected `idle-timeout: 60s`). Fixed, and `ProdProfileTest` added so CI catches it.
- After activity, the connection pool drained from 3 to 0 connections within ~105 s, so a serverless database can suspend.
- Workflows validated with `actionlint` 1.7.7.

## Manual verification performed (Phase 5, 2026-10-24)

`npm run test:e2e:live` with the backend at a 15-second interval, live TfL, Mailpit: passed. The journey details page drew TfL's real Metropolitan line shape; the scheduler created a "Lift out of service" alert for Wembley Park within one cycle; one email arrived per alert; the same cycle resolved the Metropolitan event when TfL returned it to Good Service. Screenshots were checked by eye.

## Manual verification performed (Phase 4, 2026-10-24)

Backend with a 20-second interval, Docker Postgres + Mailpit, live TfL:
- Registered, turned email on, planned Wembley Park → King's Cross (step-free) through the API and saved the Metropolitan line route.
- First cycle: 86 events created (7 lines, 63 station notices, 16 lift stations), status SUCCEEDED, **2 alerts**: "Metropolitan: Minor Delays" and the Wembley Park lift outage. TfL's identical station-notice copy of the lift notice was not alerted twice.
- Both emails arrived in Mailpit with the journey name, TfL's text and links back to the app.
- Next cycle: 0 created, 0 alerts. Account deletion removed the alerts and deliveries.

## Manual verification performed (Phase 3, 2026-10-24)

Backend + Vite + Docker Postgres, live TfL, headless Chromium driving the real UI:
- `/saved` while signed out → `/sign-in?next=%2Fsaved` → register → returned to `/saved`.
- Cookies: `SESSION` is `HttpOnly`, `SameSite=Lax`; the CSRF token changes on sign-in.
- Planned Wembley Park → King's Cross (step-free), saved from journey details, renamed, monitoring toggled; both survived a reload. "Plan this journey" re-ran the live search with the saved preferences.
- Sign out → `/saved` guarded again; wrong password → "The email or password is incorrect."; sign in → journey still there.
- Profile name changed; account deleted → signed out, `GET /auth/me` → 401, no user, journey or session rows left.

The browser run found three bugs that unit tests had missed, all fixed with regression tests: buttons announced as "DeleteHome to work" (`sr-only` spacing), the card losing its accessible name while being renamed, and the monitoring checkbox not responding until the server answered (now optimistic with rollback).

## Manual verification performed (Phase 2, 2026-10-24)

Backend running on Docker Postgres, frontend on Vite dev server, live TfL API, headless Chromium:
- Wembley Park → King's Cross with step-free preferences → 3 real Metropolitan line options, with the live Metropolitan "Minor Delays" alert shown on the results.
- Journey details, live status, station (`/stations/940GZZLUWYP`) and 404 pages all render with no console errors.
- Station page found two TfL data problems, both now handled: the accessibility note is cut off by TfL at 255 characters, and the Wembley Park lift notice was repeated as a station disruption.
- `GET /api/v1/journeys?from=abc…` → 400 RFC 9457 problem with a field error.

## Manual verification performed (Phase 1)

Against the live TfL API with the app running locally on Docker Postgres:
- `GET /api/v1/stations?query=Wembley` → 4 real stations with ICS codes; a repeat search is served from cache (265 ms → 4 ms).
- `GET /api/v1/lines/status` → 20 lines, including the six named Overground lines, with real disruptions.
- TfL unreachable (base URL pointed at a closed port) → one retry, then 503 problem JSON; no URL or key in the logs.
